package project.group1.commutemate.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small, hardcoded gazetteer of commute-relevant locations around
 * SFU Burnaby. Used to plot pins on the interactive map without needing
 * an external geocoding API (no key, no network call, no rate limit risk).
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

    private static final Map<String, LatLng> KNOWN_LOCATIONS = new LinkedHashMap<>();

    static {
        KNOWN_LOCATIONS.put("SFU Burnaby", new LatLng(49.2781, -122.9199));
        KNOWN_LOCATIONS.put("Production Way", new LatLng(49.2577, -122.9256));
        KNOWN_LOCATIONS.put("Metrotown", new LatLng(49.2258, -122.9989));
        KNOWN_LOCATIONS.put("Coquitlam Central", new LatLng(49.2543, -122.7935));
        KNOWN_LOCATIONS.put("Surrey Central", new LatLng(49.1896, -122.8489));
        KNOWN_LOCATIONS.put("Lougheed Town Centre", new LatLng(49.2486, -122.8988));
        KNOWN_LOCATIONS.put("Brentwood Town Centre", new LatLng(49.2680, -123.0028));
        KNOWN_LOCATIONS.put("Burnaby Central", new LatLng(49.2488, -122.9805));
        KNOWN_LOCATIONS.put("New Westminster", new LatLng(49.2057, -122.9110));
        KNOWN_LOCATIONS.put("Downtown Vancouver", new LatLng(49.2827, -123.1207));
    }

    private LocationCoordinates() {
    }

    /**
     * Looks up coordinates for a free-text location name.
     * Returns empty when nothing in the gazetteer matches.
     */
    public static Optional<LatLng> lookup(String freeTextLocation) {
        if (freeTextLocation == null || freeTextLocation.isBlank()) {
            return Optional.empty();
        }
        String normalized = freeTextLocation.trim().toLowerCase();
        for (Map.Entry<String, LatLng> entry : KNOWN_LOCATIONS.entrySet()) {
            if (normalized.contains(entry.getKey().toLowerCase())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}