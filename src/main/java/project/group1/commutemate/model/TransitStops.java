package project.group1.commutemate.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The TransLink stops CommuteMate watches: the main bus bays next to each SFU campus.
 *
 * <p>These are GTFS {@code stop_id}s, which are NOT the five-digit number printed on the
 * stop sign ({@code stop_code}) — the realtime feed only ever keys off stop_id. Look new
 * ones up in stops.txt of https://gtfs-static.translink.ca/gtfs/google_transit.zip; the
 * stop_code is listed beside each one below so they can be checked against the sign.</p>
 *
 * <p>Hardcoded on purpose, like {@link RideLocations}: the campuses never change, and it
 * saves downloading a 15 MB static feed just to resolve a handful of stops.</p>
 */
public final class TransitStops {

    /** One campus and the bus bays serving it. */
    public record Campus(String name, Set<String> stopIds) {
    }

    /** SFU Transit Exchange, bays 1-4 (stop_codes 51861, 53096, 52807, 60662). */
    private static final Campus BURNABY =
            new Campus("SFU Burnaby", Set.of("1875", "3129", "2836", "12972"));

    /** Surrey Central Station bus exchange, next door to the Surrey campus at Central City. */
    private static final Campus SURREY = new Campus("SFU Surrey", Set.of(
            "5262",    // Bay 2  (55210)
            "5667",    // Bay 4  (55612)
            "5768",    // Bay 5  (55713)
            "11764",   // Bay 5A (61677)
            "5769",    // Bay 6  (55714)
            "5793",    // Bay 7  (55738)
            "5045",    // Bay 8  (54993)
            "5122",    // Bay 9  (55070)
            "5494",    // Bay 10 (55441)
            "10880",   // Bay 12 (61035)
            "10881",   // Bay 13 (61036)
            "11882")); // Bay 14 (61787)

    /** Harbour Centre sits on W Hastings, two blocks from the Waterfront Station bays. */
    private static final Campus VANCOUVER = new Campus("SFU Vancouver", Set.of(
            "938",     // Eastbound W Hastings St @ Seymour St (50930) — at Harbour Centre
            "859",     // Westbound W Hastings St @ Richards St (50852)
            "9069",    // Waterfront Station @ Bay 1 (58202)
            "40",      // Waterfront Station @ Bay 2 (50040)
            "35"));    // Waterfront Station @ Bay 3 (50035)

    /** Campuses in the order the dashboard lists them. */
    public static final List<Campus> CAMPUSES = List.of(BURNABY, SURREY, VANCOUVER);

    /** Every stop we care about, for filtering a network-wide feed in one pass. */
    public static final Set<String> ALL_STOP_IDS = CAMPUSES.stream()
            .flatMap(campus -> campus.stopIds().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    private TransitStops() {
    }

    /** The campus a stop belongs to, or empty when it is not one of ours. */
    public static Optional<Campus> campusOf(String stopId) {
        return CAMPUSES.stream()
                .filter(campus -> campus.stopIds().contains(stopId))
                .findFirst();
    }
}
