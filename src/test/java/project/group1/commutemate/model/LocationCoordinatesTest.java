package project.group1.commutemate.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the SFU-area location gazetteer used by the Epic 3 interactive map. */
class LocationCoordinatesTest {

    @Test
    void lookup_findsExactKnownName() {
        var result = LocationCoordinates.lookup("SFU Burnaby");

        assertTrue(result.isPresent());
        assertEquals(49.2781, result.get().lat(), 0.0001);
        assertEquals(-122.9199, result.get().lng(), 0.0001);
    }

    @Test
    void lookup_matchesAsSubstring_caseInsensitive() {
        var result = LocationCoordinates.lookup("metrotown station (bay 12)");

        assertTrue(result.isPresent());
    }

    @Test
    void lookup_returnsEmpty_forUnknownLocation() {
        var result = LocationCoordinates.lookup("Somewhere Nobody Has Heard Of");

        assertTrue(result.isEmpty());
    }

    @Test
    void lookup_returnsEmpty_forNullOrBlank() {
        assertTrue(LocationCoordinates.lookup(null).isEmpty());
        assertTrue(LocationCoordinates.lookup("   ").isEmpty());
    }
}