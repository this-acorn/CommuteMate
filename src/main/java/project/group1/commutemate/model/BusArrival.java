package project.group1.commutemate.model;

/** One upcoming bus at a stop. */
public record BusArrival(
        String routeNo,        // "144"
        String destination,    // "Metrotown Station"
        int minutesUntil,      // ExpectedCountdown
        String expectedTime) { // "1:45pm"
}