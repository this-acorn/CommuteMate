package project.group1.commutemate.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;

import tools.jackson.databind.JsonNode;

import project.group1.commutemate.model.BusArrival;
import project.group1.commutemate.model.ServiceAlert;
import project.group1.commutemate.model.TransitInfo;

/**
 * Fetches real-time transit information from the TransLink Open API:
 * upcoming bus arrivals at a stop (RTTI) and active service alerts
 * (GTFS-realtime). Every call degrades gracefully so a TransLink outage
 * never breaks the dashboard.
 */
@Service
public class TransitService {

    private static final Logger log = LoggerFactory.getLogger(TransitService.class);

    /** RTTI real-time bus estimates for a single stop. {stop} and {key} are filled in per request. */
    private static final String ESTIMATES_URL =
            "https://api.translink.ca/rttiapi/v1/stops/{stop}/estimates?apikey={key}&count=5";

    /** GTFS-realtime service alerts for the whole TransLink network. {key} is filled in per request. */
    private static final String ALERTS_URL =
            "https://gtfsapi.translink.ca/v3/gtfsalerts?apikey={key}";

    /** At most this many alerts on the dashboard card, so it stays readable. */
    private static final int MAX_ALERTS = 5;

    private final RestClient restClient;
    private final String apiKey;
    private final String stopNo;
    private final String stopName;

    /**
     * Takes the Spring-managed builder rather than {@code RestClient.create()} so the
     * connect/read timeouts in application.properties apply: without them a slow (rather
     * than dead) TransLink never throws, and the dashboard request thread hangs instead of
     * falling through to the "temporarily unavailable" card.
     */
    public TransitService(
            RestClient.Builder restClientBuilder,
            @Value("${translink.api.key}") String apiKey,
            @Value("${translink.stop-no}") String stopNo,
            @Value("${translink.stop-name:}") String stopName) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.stopNo = stopNo;
        this.stopName = stopName;
    }

    /**
     * Bundles everything the dashboard needs. The two feeds fail independently:
     * when either call fails (e.g. the TransLink API is unreachable) its
     * "available" flag is false, so the view can show an error for that section
     * while the rest of the page keeps working.
     */
    public TransitInfo getTransitInfo() {
        List<BusArrival> arrivals = fetchArrivals();   // null signals the API is unreachable
        List<ServiceAlert> alerts = fetchAlerts();     // null signals the feed is unreachable
        boolean apiAvailable = (arrivals != null);
        boolean alertsAvailable = (alerts != null);

        return new TransitInfo(
                stopNo,
                stopName,
                apiAvailable,
                apiAvailable ? arrivals : List.of(),
                alertsAvailable,
                alertsAvailable ? alerts : List.of());
    }

    /**
     * Calls the TransLink RTTI API for the configured stop.
     * Returns the list of upcoming buses (possibly empty when the stop has no
     * service right now), or null when the API call itself fails.
     */
    private List<BusArrival> fetchArrivals() {
        try {
            JsonNode root = restClient.get()
                    .uri(ESTIMATES_URL, stopNo, apiKey)
                    .accept(MediaType.APPLICATION_JSON)   // TransLink returns XML unless we ask for JSON
                    .retrieve()
                    // 404 is how TransLink says "no stops found" / "no service right now", which
                    // really is "no upcoming buses", so swallow it and let parseArrivals return an
                    // empty list below. Every other 4xx (401 bad key, 403 no access, 429 rate
                    // limited) is a real failure on our side and must not be shown to riders as
                    // "no buses due", so let the default handler throw and fail the call.
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, response) -> { })
                    .body(JsonNode.class);

            return parseArrivals(root);
        } catch (Exception e) {
            // Riders only ever see "unavailable", so log the cause: a bad key or a hit rate limit
            // is indistinguishable from a TransLink outage otherwise.
            log.warn("TransLink RTTI call for stop {} failed: {}", stopNo, e.toString());
            return null;   // network error / auth / rate limit / 5xx -> API unavailable
        }
    }

    /**
     * Turns a raw RTTI estimates payload into a flat list of upcoming buses.
     * A successful response is a JSON array of routes, each holding a list of
     * schedules; anything else (an error object, empty body) yields an empty list.
     */
    static List<BusArrival> parseArrivals(JsonNode root) {
        List<BusArrival> arrivals = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return arrivals;
        }
        for (JsonNode route : root) {
            String routeNo = route.path("RouteNo").asString("").trim();
            for (JsonNode schedule : route.path("Schedules")) {
                arrivals.add(new BusArrival(
                        routeNo,
                        schedule.path("Destination").asString("").trim(),
                        schedule.path("ExpectedCountdown").asInt(0),
                        schedule.path("ExpectedLeaveTime").asString("").trim()));
            }
        }
        return arrivals;
    }

    /**
     * Active service alerts from the TransLink GTFS-realtime feed (protobuf).
     * Returns the active alerts (an empty list when the network has none), or
     * null when the feed call itself fails, so the dashboard can tell
     * "no alerts right now" apart from "we could not check".
     */
    private List<ServiceAlert> fetchAlerts() {
        try {
            byte[] feed = restClient.get()
                    .uri(ALERTS_URL, apiKey)
                    .retrieve()
                    .body(byte[].class);
            if (feed == null) {
                log.warn("TransLink alerts feed returned an empty body");
                return null;   // empty body -> we never got a usable feed
            }

            FeedMessage message = FeedMessage.parseFrom(feed);
            List<ServiceAlert> alerts = new ArrayList<>();
            for (FeedEntity entity : message.getEntityList()) {
                if (!entity.hasAlert()) {
                    continue;
                }
                Alert alert = entity.getAlert();
                String title = firstTranslation(alert.getHeaderText());
                if (title.isBlank()) {
                    continue;
                }
                alerts.add(new ServiceAlert(title, firstTranslation(alert.getDescriptionText())));
                if (alerts.size() >= MAX_ALERTS) {
                    break;
                }
            }
            return alerts;
        } catch (Exception e) {
            log.warn("TransLink alerts feed call failed: {}", e.toString());
            return null;   // network error / 5xx / unparseable protobuf -> feed unavailable
        }
    }

    /** First available translation of a GTFS-realtime text field, or "" when there is none. */
    private static String firstTranslation(TranslatedString text) {
        if (text == null || text.getTranslationCount() == 0) {
            return "";
        }
        return text.getTranslation(0).getText().trim();
    }
}
