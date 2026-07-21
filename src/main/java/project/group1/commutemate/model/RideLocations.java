package project.group1.commutemate.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The commute stops CommuteMate knows about, in one place: the list a driver picks
 * from when publishing a ride (Epic 3 list-based matching) and the coordinates the
 * ride-details map drops pins with. Keeping both here means every selectable stop
 * gets a pin, and every pin is a stop a driver can actually choose.
 *
 * <p>Coordinates are hardcoded on purpose — no geocoding API means no key, no network
 * call, and no rate limit. They are approximate: close enough to place a map pin.</p>
 */
public final class RideLocations {

    /**
     * A known stop. {@code aliases} are the shorter spellings that show up in
     * free-text ride data, so an older ride stored as "Metrotown" still finds its pin.
     */
    public record Stop(String name, double lat, double lng, List<String> aliases) {
        Stop(String name, double lat, double lng) {
            this(name, lat, lng, List.of());
        }
    }

    public static final String DEFAULT_PICKUP = "Metrotown Station";
    public static final String DEFAULT_DESTINATION = "SFU Burnaby — AQ";

    /** Transit hubs and neighbourhoods at the bottom of the mountain. */
    private static final List<Stop> COMMUNITY = List.of(
            new Stop("Metrotown Station", 49.2258, -122.9989, List.of("Metrotown")),
            new Stop("Production Way–University", 49.2577, -122.9256, List.of("Production Way")),
            new Stop("Lougheed Town Centre", 49.2486, -122.8988),
            new Stop("Brentwood Town Centre", 49.2680, -123.0028),
            new Stop("Burquitlam Station", 49.2614, -122.8899, List.of("Burquitlam")),
            new Stop("Edmonds Station", 49.2126, -122.9583, List.of("Edmonds")),
            new Stop("Burnaby Central", 49.2488, -122.9805),
            new Stop("Coquitlam Central", 49.2543, -122.7935),
            new Stop("New Westminster", 49.2057, -122.9110),
            new Stop("Surrey Central", 49.1896, -122.8489),
            new Stop("Downtown Vancouver", 49.2827, -123.1207));

    /** Stops on the SFU Burnaby campus. */
    private static final List<Stop> CAMPUS = List.of(
            new Stop("SFU Burnaby — AQ", 49.2781, -122.9199, List.of("SFU Burnaby")),
            new Stop("SFU Burnaby — West Mall", 49.2772, -122.9236, List.of("West Mall")),
            new Stop("SFU Burnaby — Convocation Mall", 49.2785, -122.9182, List.of("Convocation Mall")),
            new Stop("SFU Residence", 49.2801, -122.9139));

    public static final List<String> COMMUNITY_STOPS = names(COMMUNITY);
    public static final List<String> CAMPUS_STOPS = names(CAMPUS);
    public static final List<String> ALL =
            Stream.concat(COMMUNITY_STOPS.stream(), CAMPUS_STOPS.stream()).toList();

    private static final List<Stop> ALL_STOPS =
            Stream.concat(COMMUNITY.stream(), CAMPUS.stream()).toList();

    /**
     * Every spelling a stop answers to, longest first, so "SFU Burnaby — Convocation Mall"
     * wins over the shorter "SFU Burnaby" alias instead of being shadowed by it.
     */
    private static final List<Map.Entry<String, Stop>> MATCH_KEYS = ALL_STOPS.stream()
            .flatMap(stop -> Stream.concat(Stream.of(stop.name()), stop.aliases().stream())
                    .map(key -> Map.entry(key.toLowerCase(Locale.ROOT), stop)))
            .sorted(Comparator.comparingInt(
                    (Map.Entry<String, Stop> entry) -> entry.getKey().length()).reversed())
            .toList();

    private RideLocations() {
    }

    /**
     * Finds the stop a free-text location refers to, matching case-insensitively on
     * the stop name or one of its aliases as a substring. Empty when nothing matches.
     */
    public static Optional<Stop> match(String freeTextLocation) {
        if (freeTextLocation == null || freeTextLocation.isBlank()) {
            return Optional.empty();
        }
        String normalized = freeTextLocation.trim().toLowerCase(Locale.ROOT);
        return MATCH_KEYS.stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** Returns the canonical spelling of a submitted stop, or null when it is not on the list. */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return ALL.stream()
                .filter(stop -> stop.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }

    private static List<String> names(List<Stop> stops) {
        return stops.stream().map(Stop::name).toList();
    }
}
