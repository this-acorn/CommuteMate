package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

import project.group1.commutemate.model.BusArrival;
import project.group1.commutemate.model.CampusDepartures;
import project.group1.commutemate.model.ServiceAlert;

/** Unit tests for reading a GTFS-realtime feed, exercised without any network call. */
class TransitServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

    /** 1:00pm in Vancouver, so a departure 45 minutes out lands on a memorable 1:45pm. */
    private static final Instant NOW = Instant.parse("2026-07-20T20:00:00Z");

    private static final String BURNABY_BAY_1 = "1875";
    private static final String BURNABY_BAY_2 = "3129";
    private static final String SURREY_BAY_2 = "5262";
    private static final String VANCOUVER_WATERFRONT = "9069";

    private final RouteCatalog routes = new RouteCatalog();

    private static FeedMessage feedOf(TripUpdate... tripUpdates) {
        FeedMessage.Builder feed = newFeed();
        int id = 0;
        for (TripUpdate update : tripUpdates) {
            feed.addEntity(FeedEntity.newBuilder()
                    .setId(String.valueOf(id++))
                    .setTripUpdate(update)
                    .build());
        }
        return feed.build();
    }

    private static FeedMessage.Builder newFeed() {
        return FeedMessage.newBuilder()
                .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build());
    }

    /** A trip on the given route that departs the given stop at {@code NOW + minutes}. */
    private static TripUpdate departure(String routeId, String stopId, long minutes) {
        return TripUpdate.newBuilder()
                .setTrip(TripDescriptor.newBuilder().setRouteId(routeId).build())
                .addStopTimeUpdate(StopTimeUpdate.newBuilder()
                        .setStopId(stopId)
                        .setDeparture(StopTimeEvent.newBuilder()
                                .setTime(NOW.plusSeconds(minutes * 60).getEpochSecond())
                                .build())
                        .build())
                .build();
    }

    private TransitService.Departures parse(FeedMessage feed) {
        return TransitService.parseDepartures(feed, NOW, VANCOUVER, routes);
    }

    private List<CampusDepartures> campuses(FeedMessage feed) {
        return parse(feed).byCampus();
    }

    // --- departures ---

    /** Campus lookup by name, so tests do not depend on list positions. */
    private static CampusDepartures campus(List<CampusDepartures> campuses, String name) {
        return campuses.stream()
                .filter(entry -> entry.campus().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no campus named " + name));
    }

    @Test
    void mapsRouteIdToTheNumberAndNameRidersKnow() {
        // 6657 is route 144 in the static GTFS catalog we ship.
        List<CampusDepartures> campuses = campuses(feedOf(departure("6657", BURNABY_BAY_1, 45)));

        BusArrival bus = campus(campuses, "SFU Burnaby").arrivals().get(0);
        assertEquals("144", bus.routeNo());
        assertEquals("SFU Exchange/Metrotown Station", bus.destination());
        assertEquals(45, bus.minutesUntil());
        assertEquals("1:45pm", bus.expectedTime());
    }

    @Test
    void groupsDeparturesByCampusInAFixedOrder() {
        List<CampusDepartures> campuses = campuses(feedOf(
                departure("6657", VANCOUVER_WATERFRONT, 20),
                departure("6658", SURREY_BAY_2, 15),
                departure("6656", BURNABY_BAY_1, 10)));

        assertEquals(List.of("SFU Burnaby", "SFU Surrey", "SFU Vancouver"),
                campuses.stream().map(CampusDepartures::campus).toList());
    }

    /**
     * A campus that drops off the card looks broken, and SFU Burnaby is a terminus that
     * genuinely runs dry between buses — so every campus is always listed.
     */
    @Test
    void listsEveryCampusEvenWhenNothingIsDueThere() {
        List<CampusDepartures> campuses = campuses(feedOf(departure("6657", SURREY_BAY_2, 12)));

        assertEquals(List.of("SFU Burnaby", "SFU Surrey", "SFU Vancouver"),
                campuses.stream().map(CampusDepartures::campus).toList());
        assertTrue(campus(campuses, "SFU Burnaby").arrivals().isEmpty());
        assertEquals(1, campus(campuses, "SFU Surrey").arrivals().size());
    }

    @Test
    void ignoresDeparturesFromStopsWeDoNotWatch() {
        List<CampusDepartures> campuses = campuses(feedOf(departure("6657", "99999", 10)));

        assertTrue(campuses.stream().allMatch(entry -> entry.arrivals().isEmpty()));
    }

    @Test
    void dropsBusesThatHaveAlreadyGone() {
        List<BusArrival> burnaby = campus(campuses(feedOf(
                departure("6657", BURNABY_BAY_1, -10),   // left ten minutes ago
                departure("6658", BURNABY_BAY_1, 12))), "SFU Burnaby").arrivals();

        assertEquals(1, burnaby.size());
        assertEquals("145", burnaby.get(0).routeNo());
    }

    /** A bus at the stop right now should read as due, not as a negative countdown. */
    @Test
    void reportsABusDueRightNowAsZeroMinutes() {
        List<CampusDepartures> campuses = campuses(feedOf(departure("6657", BURNABY_BAY_1, 0)));

        assertEquals(0, campus(campuses, "SFU Burnaby").arrivals().get(0).minutesUntil());
    }

    @Test
    void ordersSoonestFirstAndKeepsAtMostThreePerCampus() {
        List<CampusDepartures> campuses = campuses(feedOf(
                departure("6657", BURNABY_BAY_1, 50),
                departure("6658", BURNABY_BAY_2, 5),
                departure("6656", BURNABY_BAY_1, 40),
                departure("6657", BURNABY_BAY_2, 30),
                departure("6658", BURNABY_BAY_1, 20)));

        assertEquals(List.of(5, 20, 30),
                campus(campuses, "SFU Burnaby").arrivals().stream()
                        .map(BusArrival::minutesUntil).toList());
    }

    /** The last stop of a trip has an arrival but no departure; the bus still matters. */
    @Test
    void fallsBackToArrivalTimeWhenThereIsNoDeparture() {
        TripUpdate arrivalOnly = TripUpdate.newBuilder()
                .setTrip(TripDescriptor.newBuilder().setRouteId("6657").build())
                .addStopTimeUpdate(StopTimeUpdate.newBuilder()
                        .setStopId(BURNABY_BAY_1)
                        .setArrival(StopTimeEvent.newBuilder()
                                .setTime(NOW.plusSeconds(15 * 60).getEpochSecond())
                                .build())
                        .build())
                .build();

        assertEquals(15, campus(campuses(feedOf(arrivalOnly)), "SFU Burnaby")
                .arrivals().get(0).minutesUntil());
    }

    /** A stop update with no predicted time at all tells us nothing, so skip it. */
    @Test
    void skipsStopUpdatesWithoutAnyPredictedTime() {
        TripUpdate noTime = TripUpdate.newBuilder()
                .setTrip(TripDescriptor.newBuilder().setRouteId("6657").build())
                .addStopTimeUpdate(StopTimeUpdate.newBuilder().setStopId(BURNABY_BAY_1).build())
                .build();

        assertTrue(campus(campuses(feedOf(noTime)), "SFU Burnaby").arrivals().isEmpty());
    }

    /** A route missing from the catalog must not hide the bus. */
    @Test
    void fallsBackToTheRawRouteIdWhenTheCatalogHasNoEntry() {
        List<CampusDepartures> campuses = campuses(feedOf(departure("no-such-route", BURNABY_BAY_1, 7)));

        assertEquals("no-such-route", campus(campuses, "SFU Burnaby").arrivals().get(0).routeNo());
    }

    @Test
    void treatsAFeedWithNoTripUpdatesAsNoBusesDueAnywhere() {
        assertTrue(campuses(feedOf()).stream().allMatch(entry -> entry.arrivals().isEmpty()));
    }

    /** Alert relevance keys off routes, so every route touching our stops has to be collected. */
    @Test
    void collectsTheRoutesServingOurStopsEvenWhenTheBusHasGone() {
        TransitService.Departures departures = parse(feedOf(
                departure("6657", BURNABY_BAY_1, -20),   // already gone, still one of our routes
                departure("6658", SURREY_BAY_2, 10),
                departure("6612", "99999", 10)));        // not our stop

        assertEquals(Set.of("6657", "6658"), departures.routeIds());
    }

    // --- alerts ---

    private static FeedMessage alertFeed(String title, EntitySelector... informedEntities) {
        Alert.Builder alert = Alert.newBuilder().setHeaderText(text(title));
        for (EntitySelector entity : informedEntities) {
            alert.addInformedEntity(entity);
        }
        return newFeed()
                .addEntity(FeedEntity.newBuilder().setId("a").setAlert(alert.build()).build())
                .build();
    }

    private static TranslatedString text(String value) {
        return TranslatedString.newBuilder()
                .addTranslation(TranslatedString.Translation.newBuilder()
                        .setText(value)
                        .setLanguage("en")
                        .build())
                .build();
    }

    private static List<ServiceAlert> alerts(FeedMessage feed) {
        return TransitService.parseAlerts(feed, Set.of(BURNABY_BAY_1), Set.of("6657"));
    }

    @Test
    void keepsAlertsForRoutesServingOurCampuses() {
        List<ServiceAlert> kept = alerts(alertFeed("144 detour",
                EntitySelector.newBuilder().setRouteId("6657").build()));

        assertEquals(1, kept.size());
        assertEquals("144 detour", kept.get(0).title());
    }

    @Test
    void keepsAlertsNamingOneOfOurStops() {
        assertEquals(1, alerts(alertFeed("Bay closed",
                EntitySelector.newBuilder().setStopId(BURNABY_BAY_1).build())).size());
    }

    /** A notice pinned to the agency with no route or stop applies to the whole network. */
    @Test
    void keepsNetworkWideAlerts() {
        assertEquals(1, alerts(alertFeed("Holiday schedule",
                EntitySelector.newBuilder().setAgencyId("TL").build())).size());
    }

    @Test
    void dropsAlertsForRoutesAndStopsThatAreNotOurs() {
        assertTrue(alerts(alertFeed("Langley detour",
                EntitySelector.newBuilder().setRouteId("9999").build())).isEmpty());
        assertTrue(alerts(alertFeed("Other stop closed",
                EntitySelector.newBuilder().setStopId("99999").build())).isEmpty());
    }

    @Test
    void dropsAlertsWithNoHeadline() {
        assertTrue(alerts(alertFeed("",
                EntitySelector.newBuilder().setRouteId("6657").build())).isEmpty());
    }
}
