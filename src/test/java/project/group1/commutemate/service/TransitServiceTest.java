package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

import project.group1.commutemate.model.BusArrival;

/** Unit tests for picking our stop's departures out of a GTFS-realtime feed, without any network call. */
class TransitServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

    /** 1:00pm in Vancouver, so a departure 45 minutes out lands on a memorable 1:45pm. */
    private static final Instant NOW = Instant.parse("2026-07-20T20:00:00Z");

    /** SFU Transit Exchange bays 1 and 2. */
    private static final Set<String> OUR_STOPS = Set.of("1875", "3129");

    private final RouteCatalog routes = new RouteCatalog();

    private static FeedMessage feedOf(TripUpdate... tripUpdates) {
        FeedMessage.Builder feed = FeedMessage.newBuilder()
                .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build());
        int id = 0;
        for (TripUpdate update : tripUpdates) {
            feed.addEntity(FeedEntity.newBuilder()
                    .setId(String.valueOf(id++))
                    .setTripUpdate(update)
                    .build());
        }
        return feed.build();
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

    private List<BusArrival> parse(FeedMessage feed) {
        return TransitService.parseArrivals(feed, OUR_STOPS, NOW, VANCOUVER, routes);
    }

    @Test
    void mapsRouteIdToTheNumberAndNameRidersKnow() {
        // 6657 is route 144 in the static GTFS catalog we ship.
        List<BusArrival> arrivals = parse(feedOf(departure("6657", "1875", 45)));

        assertEquals(1, arrivals.size());
        BusArrival bus = arrivals.get(0);
        assertEquals("144", bus.routeNo());
        assertEquals("SFU Exchange/Metrotown Station", bus.destination());
        assertEquals(45, bus.minutesUntil());
        assertEquals("1:45pm", bus.expectedTime());
    }

    @Test
    void ignoresDeparturesFromOtherStops() {
        assertTrue(parse(feedOf(departure("6657", "99999", 10))).isEmpty());
    }

    @Test
    void dropsBusesThatHaveAlreadyGone() {
        List<BusArrival> arrivals = parse(feedOf(
                departure("6657", "1875", -10),   // left ten minutes ago
                departure("6658", "1875", 12)));

        assertEquals(1, arrivals.size());
        assertEquals("145", arrivals.get(0).routeNo());
    }

    /** A bus at the stop right now should read "Due now", not a negative countdown. */
    @Test
    void reportsABusDueRightNowAsZeroMinutes() {
        List<BusArrival> arrivals = parse(feedOf(departure("6657", "1875", 0)));

        assertEquals(1, arrivals.size());
        assertEquals(0, arrivals.get(0).minutesUntil());
    }

    @Test
    void ordersSoonestFirstAndKeepsAtMostFive() {
        List<BusArrival> arrivals = parse(feedOf(
                departure("6657", "1875", 50),
                departure("6658", "3129", 5),
                departure("6656", "1875", 40),
                departure("6657", "3129", 30),
                departure("6658", "1875", 20),
                departure("6656", "3129", 10),
                departure("6657", "1875", 60)));

        assertEquals(5, arrivals.size());
        assertEquals(List.of(5, 10, 20, 30, 40),
                arrivals.stream().map(BusArrival::minutesUntil).toList());
    }

    /** The last stop of a trip has an arrival but no departure; the bus still matters. */
    @Test
    void fallsBackToArrivalTimeWhenThereIsNoDeparture() {
        TripUpdate arrivalOnly = TripUpdate.newBuilder()
                .setTrip(TripDescriptor.newBuilder().setRouteId("6657").build())
                .addStopTimeUpdate(StopTimeUpdate.newBuilder()
                        .setStopId("1875")
                        .setArrival(StopTimeEvent.newBuilder()
                                .setTime(NOW.plusSeconds(15 * 60).getEpochSecond())
                                .build())
                        .build())
                .build();

        List<BusArrival> arrivals = parse(feedOf(arrivalOnly));

        assertEquals(1, arrivals.size());
        assertEquals(15, arrivals.get(0).minutesUntil());
    }

    /** A stop update with no predicted time at all tells us nothing, so skip it. */
    @Test
    void skipsStopUpdatesWithoutAnyPredictedTime() {
        TripUpdate noTime = TripUpdate.newBuilder()
                .setTrip(TripDescriptor.newBuilder().setRouteId("6657").build())
                .addStopTimeUpdate(StopTimeUpdate.newBuilder().setStopId("1875").build())
                .build();

        assertTrue(parse(feedOf(noTime)).isEmpty());
    }

    /** A route missing from the catalog must not hide the bus. */
    @Test
    void fallsBackToTheRawRouteIdWhenTheCatalogHasNoEntry() {
        List<BusArrival> arrivals = parse(feedOf(departure("no-such-route", "1875", 7)));

        assertEquals(1, arrivals.size());
        assertEquals("no-such-route", arrivals.get(0).routeNo());
    }

    @Test
    void treatsAFeedWithNoTripUpdatesAsNoUpcomingBuses() {
        assertTrue(parse(feedOf()).isEmpty());
    }
}
