package project.group1.commutemate.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the shared stop list backing the create form and the ride-details map. */
class RideLocationsTest {

    @Test
    void everySelectableStopHasMapCoordinates() {
        for (String stop : RideLocations.ALL) {
            assertTrue(LocationCoordinates.lookup(stop).isPresent(),
                    "no map pin for a stop a driver can pick: " + stop);
        }
    }

    @Test
    void campusStopsResolveToTheirOwnPinInsteadOfTheGenericSfuOne() {
        var convocation = LocationCoordinates.lookup("SFU Burnaby — Convocation Mall").orElseThrow();
        var aq = LocationCoordinates.lookup("SFU Burnaby — AQ").orElseThrow();

        assertEquals(49.2785, convocation.lat(), 0.0001);
        assertEquals(-122.9182, convocation.lng(), 0.0001);
        assertTrue(convocation.lat() != aq.lat() || convocation.lng() != aq.lng(),
                "campus stops should not all share one pin");
    }

    @Test
    void canonicalAcceptsListedStopsAndRejectsEverythingElse() {
        assertEquals("Metrotown Station", RideLocations.canonical("  metrotown station "));
        assertEquals(null, RideLocations.canonical("Metrotown"));
        assertEquals(null, RideLocations.canonical(null));
    }
}
