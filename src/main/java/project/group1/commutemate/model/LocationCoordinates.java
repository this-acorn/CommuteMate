package project.group1.commutemate.model;

import java.util.Optional;

/**
 * Coordinate lookup for the interactive map on the ride details page.
 *
 * The gazetteer itself lives in {@link RideLocations}, next to the list a driver
 * picks from when publishing a ride, so the two can never drift apart: every stop
 * a driver can choose has a pin, and every pin is a stop a driver can choose.
 *
 * Matching is case-insensitive and looks for the known place name as a
 * substring of the ride's free-text location, e.g. a ride "from" of
 * "Metrotown Station (Bay 12)" still matches "Metrotown".
 *
 * Locations not in this list simply don't get a pin — the rest of the
 * ride details page is unaffected (graceful degradation, consistent with
 * how weather/transit handle unavailable data elsewhere in the app).
 */
public final class LocationCoordinates {

    /** Simple lat/lng pair for a named location. */
    public record LatLng(double lat, double lng) {
    }

    private LocationCoordinates() {
    }

    /**
     * Looks up coordinates for a free-text location name.
     * Returns empty when nothing in the gazetteer matches.
     */
    public static Optional<LatLng> lookup(String freeTextLocation) {
        return RideLocations.match(freeTextLocation)
                .map(stop -> new LatLng(stop.lat(), stop.lng()));
    }
}
