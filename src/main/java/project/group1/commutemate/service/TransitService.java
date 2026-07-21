package project.group1.commutemate.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;

import project.group1.commutemate.model.BusArrival;
import project.group1.commutemate.model.ServiceAlert;
import project.group1.commutemate.model.TransitInfo;

/**
 * Fetches real-time transit information from TransLink's GTFS-realtime V3 API:
 * upcoming departures from our stop (trip updates) and active service alerts.
 * Every call degrades gracefully so a TransLink outage never breaks the dashboard.
 *
 * <p>This used to call the RTTI Open API, which TransLink retired on 3 December
 * 2024 — its host stopped resolving, so every lookup failed no matter what API
 * key was configured. GTFS-realtime is the documented replacement.</p>
 */
@Service
public class TransitService {

    private static final Logger log = LoggerFactory.getLogger(TransitService.class);

    /** Real-time trip updates for the whole network. {key} is filled in per request. */
    private static final String TRIP_UPDATES_URL =
            "https://gtfsapi.translink.ca/v3/gtfsrealtime?apikey={key}";

    /** GTFS-realtime service alerts for the whole TransLink network. {key} is filled in per request. */
    private static final String ALERTS_URL =
            "https://gtfsapi.translink.ca/v3/gtfsalerts?apikey={key}";

    /** At most this many alerts on the dashboard card, so it stays readable. */
    private static final int MAX_ALERTS = 5;

    /** At most this many upcoming buses, matching what the card has room for. */
    private static final int MAX_ARRIVALS = 5;

    /** A bus that left up to this long ago is still worth showing as "Due now". */
    private static final Duration JUST_MISSED = Duration.ofSeconds(30);

    private static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

    private final RestClient restClient;
    private final RouteCatalog routeCatalog;
    private final Clock clock;
    private final String apiKey;
    private final Set<String> stopIds;
    private final String stopName;

    /**
     * Takes the Spring-managed builder rather than {@code RestClient.create()} so the
     * connect/read timeouts in application.properties apply: without them a slow (rather
     * than dead) TransLink never throws, and the dashboard request thread hangs instead of
     * falling through to the "temporarily unavailable" card.
     */
    public TransitService(
            RestClient.Builder restClientBuilder,
            RouteCatalog routeCatalog,
            Clock clock,
            @Value("${translink.api.key}") String apiKey,
            @Value("${translink.stop-ids}") String stopIds,
            @Value("${translink.stop-name:}") String stopName) {
        this.restClient = restClientBuilder.build();
        this.routeCatalog = routeCatalog;
        this.clock = clock;
        this.apiKey = apiKey;
        this.stopIds = parseStopIds(stopIds);
        this.stopName = stopName;
    }

    private static Set<String> parseStopIds(String configured) {
        return Arrays.stream(configured.split(","))
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
                stopName,
                apiAvailable,
                apiAvailable ? arrivals : List.of(),
                alertsAvailable,
                alertsAvailable ? alerts : List.of());
    }

    /**
     * Upcoming departures from our stop, or null when the feed call itself fails.
     * An empty list is a real answer: GTFS-realtime only reports trips that are
     * actually running, so outside service hours there is genuinely nothing due.
     */
    private List<BusArrival> fetchArrivals() {
        try {
            byte[] feed = restClient.get()
                    .uri(TRIP_UPDATES_URL, apiKey)
                    .retrieve()
                    .body(byte[].class);
            if (feed == null) {
                log.warn("TransLink trip updates feed returned an empty body");
                return null;
            }

            return parseArrivals(FeedMessage.parseFrom(feed), stopIds, clock.instant(),
                    clock.getZone(), routeCatalog);
        } catch (Exception e) {
            // Riders only ever see "unavailable", so log the cause: a bad key or a hit rate limit
            // is indistinguishable from a TransLink outage otherwise.
            log.warn("TransLink trip updates call for stops {} failed: {}", stopIds, e.toString());
            return null;   // network error / auth / rate limit / 5xx -> API unavailable
        }
    }

    /**
     * Picks the departures that serve our stop out of a network-wide trip update feed.
     * Buses already gone are dropped, the rest are ordered soonest-first.
     */
    static List<BusArrival> parseArrivals(FeedMessage feed, Set<String> stopIds, Instant now,
                                          ZoneId zone, RouteCatalog routeCatalog) {
        record Departure(Instant when, BusArrival arrival) {
        }

        Instant earliest = now.minus(JUST_MISSED);
        List<Departure> departures = new ArrayList<>();

        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasTripUpdate()) {
                continue;
            }
            TripUpdate tripUpdate = entity.getTripUpdate();
            for (TripUpdate.StopTimeUpdate stop : tripUpdate.getStopTimeUpdateList()) {
                if (!stopIds.contains(stop.getStopId())) {
                    continue;
                }
                Optional<Instant> when = departureTime(stop);
                if (when.isEmpty() || when.get().isBefore(earliest)) {
                    continue;   // no predicted time, or the bus has already gone
                }
                departures.add(new Departure(when.get(),
                        toArrival(tripUpdate, when.get(), now, zone, routeCatalog)));
            }
        }

        return departures.stream()
                .sorted(Comparator.comparing(Departure::when))
                .limit(MAX_ARRIVALS)
                .map(Departure::arrival)
                .toList();
    }

    /**
     * When the bus leaves our stop. Departure is what a waiting rider cares about;
     * arrival is the fallback for the last stop of a trip, which has no departure.
     */
    private static Optional<Instant> departureTime(TripUpdate.StopTimeUpdate stop) {
        if (stop.hasDeparture() && stop.getDeparture().getTime() > 0) {
            return Optional.of(Instant.ofEpochSecond(stop.getDeparture().getTime()));
        }
        if (stop.hasArrival() && stop.getArrival().getTime() > 0) {
            return Optional.of(Instant.ofEpochSecond(stop.getArrival().getTime()));
        }
        return Optional.empty();
    }

    private static BusArrival toArrival(TripUpdate tripUpdate, Instant when, Instant now,
                                        ZoneId zone, RouteCatalog routeCatalog) {
        RouteCatalog.Route route = routeCatalog.find(tripUpdate.getTrip().getRouteId())
                // An unknown id still beats hiding the bus: show the raw id rather than a blank row.
                .orElseGet(() -> new RouteCatalog.Route(tripUpdate.getTrip().getRouteId(), ""));

        long minutes = Math.max(0, Duration.between(now, when).toMinutes());
        String clockTime = CLOCK_TIME.format(when.atZone(zone)).toLowerCase(Locale.ENGLISH);

        return new BusArrival(route.number(), route.name(), (int) minutes, clockTime);
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
