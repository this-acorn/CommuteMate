package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import project.group1.commutemate.model.BusArrival;

/** Unit tests for the RTTI estimates parsing, exercised without any network call. */
class TransitServiceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesEveryScheduleAcrossEveryRoute() {
        String json = """
                [
                  {
                    "RouteNo": "144",
                    "Schedules": [
                      { "Destination": "METROTOWN STN", "ExpectedCountdown": 5,  "ExpectedLeaveTime": "1:45pm" },
                      { "Destination": "METROTOWN STN", "ExpectedCountdown": 20, "ExpectedLeaveTime": "2:00pm" }
                    ]
                  },
                  {
                    "RouteNo": "145",
                    "Schedules": [
                      { "Destination": "PRODUCTION STN", "ExpectedCountdown": 8, "ExpectedLeaveTime": "1:48pm" }
                    ]
                  }
                ]
                """;

        List<BusArrival> arrivals = TransitService.parseArrivals(mapper.readTree(json));

        assertEquals(3, arrivals.size());
        BusArrival first = arrivals.get(0);
        assertEquals("144", first.routeNo());
        assertEquals("METROTOWN STN", first.destination());
        assertEquals(5, first.minutesUntil());
        assertEquals("1:45pm", first.expectedTime());
        assertEquals("145", arrivals.get(2).routeNo());
    }

    @Test
    void treatsAnErrorObjectAsNoUpcomingBuses() {
        // TransLink returns an object like this (not an array) when a stop has no service.
        JsonNode errorObject = mapper.readTree("{\"Code\":\"3011\",\"Message\":\"No stops found.\"}");

        assertTrue(TransitService.parseArrivals(errorObject).isEmpty());
    }

    @Test
    void treatsNullAndEmptyArrayAsNoUpcomingBuses() {
        assertTrue(TransitService.parseArrivals(null).isEmpty());
        assertTrue(TransitService.parseArrivals(mapper.readTree("[]")).isEmpty());
    }
}
