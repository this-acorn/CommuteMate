package project.group1.commutemate.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import project.group1.commutemate.model.Weather;

class WeatherServiceTest {

    private MockRestServiceServer server;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // bindTo() swaps in a stub request factory, so the service must be built
        // from this same builder afterwards.
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WeatherService(builder);
    }

    /** Stubs the Open-Meteo endpoint with the given JSON body. */
    private void stubApi(String json) {
        server.expect(requestTo(containsString("api.open-meteo.com")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    // --- getCurrentWeather() ---

    @Test
    void getCurrentWeatherMapsApiResponse() {
        stubApi("""
                {"current":{
                    "temperature_2m":12.5,
                    "weather_code":61,
                    "wind_speed_10m":8.0,
                    "wind_direction_10m":90
                }}
                """);

        Weather weather = service.getCurrentWeather().orElseThrow();

        assertEquals(12.5, weather.temperature());
        assertEquals("Rain", weather.condition());
        assertEquals(8.0, weather.windSpeed());
        assertEquals("E", weather.windDirection());
        server.verify();
    }

    @Test
    void getCurrentWeatherRequestsTheConfiguredCoordinates() {
        server.expect(requestTo(containsString("latitude=49.2781&longitude=-122.9199")))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":0,"weather_code":0,
                                    "wind_speed_10m":0,"wind_direction_10m":0}}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(service.getCurrentWeather().isPresent());
        server.verify();
    }

    @Test
    void getCurrentWeatherReturnsEmptyWhenCurrentBlockIsMissing() {
        stubApi("{\"hourly\":{}}");

        assertEquals(Optional.empty(), service.getCurrentWeather());
    }

    @Test
    void getCurrentWeatherReturnsEmptyWhenAFieldIsMissing() {
        stubApi("""
                {"current":{"temperature_2m":12.5,"wind_speed_10m":8.0}}
                """);

        assertEquals(Optional.empty(), service.getCurrentWeather());
    }

    @Test
    void getCurrentWeatherReturnsEmptyOnMalformedJson() {
        stubApi("not json at all");

        assertEquals(Optional.empty(), service.getCurrentWeather());
    }

    @Test
    void getCurrentWeatherReturnsEmptyOnServerError() {
        server.expect(requestTo(containsString("api.open-meteo.com")))
                .andRespond(withServerError());

        assertEquals(Optional.empty(), service.getCurrentWeather());
    }

    // --- helpers ---

    @Test
    void describeMapsKnownWeatherCodes() {
        assertEquals("Clear sky", service.describe(0));
        assertEquals("Snow", service.describe(71));
        assertEquals("Rain", service.describe(61));
    }

    @Test
    void describeReturnsUnknownForUnmappedCode() {
        assertEquals("Unknown", service.describe(999));
    }

    @Test
    void compassConvertsDegreesToDirection() {
        assertEquals("N", service.compass(0));
        assertEquals("E", service.compass(90));
        assertEquals("S", service.compass(180));
        assertEquals("W", service.compass(270));
    }

    @Test
    void compassWrapsBackToNorthNearFullCircle() {
        assertEquals("N", service.compass(350));
        assertEquals("N", service.compass(360));
    }
}
