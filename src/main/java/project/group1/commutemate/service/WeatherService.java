package project.group1.commutemate.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import project.group1.commutemate.model.Weather;
import tools.jackson.databind.JsonNode;

/** Fetches current weather on Burnaby Mountain from the Open-Meteo API. */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=49.2781&longitude=-122.9199"
            + "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m";

    private final RestClient restClient;

    /**
     * Takes the Spring-managed builder rather than {@code RestClient.create()} so the
     * connect/read timeouts in application.properties apply: without them a slow
     * (rather than dead) Open-Meteo never throws, and the dashboard request thread
     * hangs instead of the page rendering without the weather card.
     */
    public WeatherService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * Current conditions, cached (see the "weather" cache in application.properties)
     * so dashboard reloads don't call the API every time. Spring unwraps the Optional
     * before caching, so "unless #result == null" keeps failures out of the cache and
     * the next request retries as soon as the API is back.
     */
    @Cacheable(cacheNames = "weather", unless = "#result == null")
    public Optional<Weather> getCurrentWeather() {
        try {
            JsonNode root = restClient.get()
                    .uri(URL)
                    .retrieve()
                    .body(JsonNode.class);

            return parseWeather(root);
        } catch (Exception e) {
            // Users only ever see a missing weather card, so log the cause: a network
            // error is indistinguishable from a bad response otherwise.
            log.warn("Open-Meteo call failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Maps an Open-Meteo response onto {@link Weather}, empty when fields are missing. */
    private Optional<Weather> parseWeather(JsonNode root) {
        if (root == null || !root.has("current")) {
            log.warn("Open-Meteo response has no \"current\" block");
            return Optional.empty();
        }
        JsonNode current = root.get("current");
        if (!current.has("temperature_2m") || !current.has("weather_code")
                || !current.has("wind_speed_10m") || !current.has("wind_direction_10m")) {
            log.warn("Open-Meteo \"current\" block is missing an expected field");
            return Optional.empty();
        }

        Weather weather = new Weather(
                current.get("temperature_2m").asDouble(),
                describe(current.get("weather_code").asInt()),
                current.get("wind_speed_10m").asDouble(),
                compass(current.get("wind_direction_10m").asInt()));
        return Optional.of(weather);
    }

    String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Partly cloudy";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    String compass(int degrees) {
        String[] dirs = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        int index = (int) Math.round(degrees / 45.0) % 8;
        return dirs[index];
    }
}
