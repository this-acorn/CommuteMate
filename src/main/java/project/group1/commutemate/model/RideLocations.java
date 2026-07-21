package project.group1.commutemate.model;

import java.util.List;
import java.util.stream.Stream;

/** Fixed pickup and drop-off points a driver can pick from when publishing a ride. */
public final class RideLocations {

    public static final String DEFAULT_PICKUP = "Metrotown Station";
    public static final String DEFAULT_DESTINATION = "SFU Burnaby — AQ";

    /** Transit hubs and neighbourhoods at the bottom of the mountain. */
    public static final List<String> COMMUNITY_STOPS = List.of(
            "Metrotown Station",
            "Production Way–University",
            "Lougheed Town Centre",
            "Coquitlam Central",
            "Brentwood Town Centre",
            "Burquitlam Station",
            "Edmonds Station");

    /** Stops on the SFU Burnaby campus. */
    public static final List<String> CAMPUS_STOPS = List.of(
            "SFU Burnaby — AQ",
            "SFU Burnaby — West Mall",
            "SFU Burnaby — Convocation Mall",
            "SFU Residence");

    public static final List<String> ALL =
            Stream.concat(COMMUNITY_STOPS.stream(), CAMPUS_STOPS.stream()).toList();

    private RideLocations() {
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
}
