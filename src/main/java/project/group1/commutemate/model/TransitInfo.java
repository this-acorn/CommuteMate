package project.group1.commutemate.model;

import java.util.List;

/** All transit info shown on the dashboard. */
public record TransitInfo(
        boolean apiAvailable,              // false when the trip updates call failed
        List<CampusDepartures> campuses,   // every campus, each with its next buses (possibly none)
        boolean alertsAvailable,           // false when the alerts (GTFS-realtime) feed failed
        List<ServiceAlert> alerts) {       // alerts affecting our stops and routes, newest first
}
