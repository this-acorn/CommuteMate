package project.group1.commutemate.model;

import java.util.List;

/** All transit info shown on the dashboard. */
public record TransitInfo(
        String stopName,                // stop these departures leave from, or "" when not configured
        boolean apiAvailable,           // false when the trip updates call failed
        List<BusArrival> arrivals,      // empty when no buses are due
        boolean alertsAvailable,        // false when the alerts (GTFS-realtime) feed failed
        List<ServiceAlert> alerts) {    // empty when the network has no active alerts
}
