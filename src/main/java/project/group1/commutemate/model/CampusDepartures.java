package project.group1.commutemate.model;

import java.util.List;

/** The next buses leaving the stops next to one SFU campus. */
public record CampusDepartures(
        String campus,              // "SFU Burnaby"
        List<BusArrival> arrivals) {
}
