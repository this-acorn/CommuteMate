package project.group1.commutemate.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;

import project.group1.commutemate.model.BusArrival;
import project.group1.commutemate.model.CampusDepartures;
import project.group1.commutemate.model.ServiceAlert;
import project.group1.commutemate.model.TransitInfo;
import project.group1.commutemate.model.TransitStops;

/**
 * Fetches real-time transit information from TransLink's GTFS-realtime V3 API:
 * upcoming departures from the stops next to each SFU campus, and the service
 * alerts affecting them. Every call degrades gracefully so a TransLink outage
 * never breaks the dashboard.
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

    /** At most this many buses per campus, so three campuses still fit the card. */
    private static final int MAX_ARRIVALS_PER_CAMPUS = 3;

    /** A bus that left up to this long ago is still worth showing as "Due now". */
    private static final Duration JUST_MISSED = Duration.ofSeconds(30);

    private static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

    private final RestClient restClient;
    private final RouteCatalog routeCatalog;
    private final Clock clock;
    private final String apiKey;

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
            @Value("${translink.api.key}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.routeCatalog = routeCatalog;
        this.clock = clock;
        this.apiKey = apiKey;
    }

    /** What one pass over the trip updates feed tells us about our stops. */
    record Departures(List<CampusDepartures> byCampus, Set<String> routeIds) {
    }

    /**
     * Bundles everything the dashboard needs. The two feeds fail independently:
     * when either call fails (e.g. the TransLink API is unreachable) its
     * "available" flag is false, so the view can show an error for that section
     * while the rest of the page keeps working.
     */
    public TransitInfo getTransitInfo() {
        Departures departures = fetchDepartures();       // null signals the API is unreachable
        boolean apiAvailable = (departures != null);

        // Which routes actually serve our campuses decides which alerts matter. When the
        // departures feed is down we cannot tell, so alerts fall back to stop-level matches.
        Set<String> ourRoutes = apiAvailable ? departures.routeIds() : Set.of();
        List<ServiceAlert> alerts = fetchAlerts(ourRoutes);   // null signals the feed is unreachable
        boolean alertsAvailable = (alerts != null);

        return new TransitInfo(
                apiAvailable,
                apiAvailable ? departures.byCampus() : List.of(),
                alertsAvailable,
                alertsAvailable ? alerts : List.of());
    }

    /**
     * Upcoming departures grouped by campus, or null when the feed call itself fails.
     * No departures is a real answer: GTFS-realtime only reports trips that are
     * actually running, so outside service hours there is genuinely nothing due.
     */
    private Departures fetchDepartures() {
        try {
            byte[] feed = restClient.get()
                    .uri(TRIP_UPDATES_URL, apiKey)
                    .retrieve()
                    .body(byte[].class);
            if (feed == null) {
                log.warn("TransLink trip updates feed returned an empty body");
                return null;
            }

            return parseDepartures(FeedMessage.parseFrom(feed), clock.instant(),
                    clock.getZone(), routeCatalog);
        } catch (Exception e) {
            // Riders only ever see "unavailable", so log the cause: a bad key or a hit rate limit
            // is indistinguishable from a TransLink outage otherwise.
            log.warn("TransLink trip updates call failed: {}", e.toString());
            return null;   // network error / auth / rate limit / 5xx -> API unavailable
        }
    }

    /**
     * Picks the departures serving our campuses out of a network-wide trip update feed.
     * Buses already gone are dropped, the rest are ordered soonest-first within each campus.
     *
     * <p>Every campus is returned even with nothing due, because dropping one makes the card
     * look broken: SFU Burnaby is a terminus served by a handful of routes, so between buses
     * the feed genuinely has nothing for it, and a rider should see "none due" rather than
     * watch their campus disappear.</p>
     */
    static Departures parseDepartures(FeedMessage feed, Instant now, ZoneId zone,
                                      RouteCatalog routeCatalog) {
        record Departure(Instant when, BusArrival arrival) {
        }

        Instant earliest = now.minus(JUST_MISSED);
        Map<String, List<Departure>> byCampus = new LinkedHashMap<>();
        Set<String> routeIds = new HashSet<>();

        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasTripUpdate()) {
                continue;
            }
            TripUpdate tripUpdate = entity.getTripUpdate();
            for (TripUpdate.StopTimeUpdate stop : tripUpdate.getStopTimeUpdateList()) {
                Optional<TransitStops.Campus> campus = TransitStops.campusOf(stop.getStopId());
                if (campus.isEmpty()) {
                    continue;
                }
                // Collected before the time filter: a route that served us earlier today is
                // still one of "our" routes as far as service alerts are concerned.
                routeIds.add(tripUpdate.getTrip().getRouteId());

                Optional<Instant> when = departureTime(stop);
                if (when.isEmpty() || when.get().isBefore(earliest)) {
                    continue;   // no predicted time, or the bus has already gone
                }
                byCampus.computeIfAbsent(campus.get().name(), key -> new ArrayList<>())
                        .add(new Departure(when.get(),
                                toArrival(tripUpdate, when.get(), now, zone, routeCatalog)));
            }
        }

        List<CampusDepartures> campuses = new ArrayList<>();
        for (TransitStops.Campus campus : TransitStops.CAMPUSES) {   // keep the configured order
            List<Departure> found = byCampus.getOrDefault(campus.name(), List.of());
            campuses.add(new CampusDepartures(campus.name(), found.stream()
                    .sorted(Comparator.comparing(Departure::when))
                    .limit(MAX_ARRIVALS_PER_CAMPUS)
                    .map(Departure::arrival)
                    .toList()));
        }
        return new Departures(List.copyOf(campuses), Set.copyOf(routeIds));
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
     * Service alerts that affect the stops or routes we just listed, or null when the
     * feed call itself fails, so the dashboard can tell "nothing wrong right now" apart
     * from "we could not check". Network-wide alerts are always kept.
     */
    private List<ServiceAlert> fetchAlerts(Set<String> ourRoutes) {
        try {
            byte[] feed = restClient.get()
                    .uri(ALERTS_URL, apiKey)
                    .retrieve()
                    .body(byte[].class);
            if (feed == null) {
                log.warn("TransLink alerts feed returned an empty body");
                return null;   // empty body -> we never got a usable feed
            }

            return parseAlerts(FeedMessage.parseFrom(feed), TransitStops.ALL_STOP_IDS, ourRoutes);
        } catch (Exception e) {
            log.warn("TransLink alerts feed call failed: {}", e.toString());
            return null;   // network error / 5xx / unparseable protobuf -> feed unavailable
        }
    }

    /**
     * Keeps only the alerts a rider at one of our campuses could act on. The feed covers
     * the whole network, so without this the card fills up with detours in Langley.
     */
    static List<ServiceAlert> parseAlerts(FeedMessage feed, Set<String> stopIds, Set<String> routeIds) {
        List<ServiceAlert> alerts = new ArrayList<>();
        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) {
                continue;
            }
            Alert alert = entity.getAlert();
            String title = firstTranslation(alert.getHeaderText());
            if (title.isBlank() || !affectsUs(alert, stopIds, routeIds)) {
                continue;
            }
            alerts.add(new ServiceAlert(title, firstTranslation(alert.getDescriptionText())));
            if (alerts.size() >= MAX_ALERTS) {
                break;
            }
        }
        return alerts;
    }

    /** True when an alert names one of our stops, one of our routes, or the whole network. */
    private static boolean affectsUs(Alert alert, Set<String> stopIds, Set<String> routeIds) {
        for (EntitySelector entity : alert.getInformedEntityList()) {
            if (entity.hasStopId() && stopIds.contains(entity.getStopId())) {
                return true;
            }
            if (entity.hasRouteId() && routeIds.contains(entity.getRouteId())) {
                return true;
            }
            if (entity.hasTrip() && routeIds.contains(entity.getTrip().getRouteId())) {
                return true;
            }
            // A notice pinned to the agency alone, with no route or stop, is network-wide.
            if (entity.hasAgencyId() && !entity.hasStopId() && !entity.hasRouteId()
                    && !entity.hasTrip()) {
                return true;
            }
        }
        return false;
    }

    /** First available translation of a GTFS-realtime text field, or "" when there is none. */
    private static String firstTranslation(TranslatedString text) {
        if (text == null || text.getTranslationCount() == 0) {
            return "";
        }
        return text.getTranslation(0).getText().trim();
    }
}
