package project.group1.commutemate.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps a GTFS {@code route_id} to the route number riders actually recognise.
 *
 * <p>The GTFS-realtime feed only ever carries the internal id ("6657"), never
 * "144", so the numbers come from a small extract of the static GTFS
 * {@code routes.txt} shipped in resources. Downloading the full 15 MB static
 * feed at startup would be far more than this one lookup is worth.</p>
 */
@Component
public class RouteCatalog {

    /** A route as riders know it: the number on the bus, plus where it runs. */
    public record Route(String number, String name) {
    }

    private static final Logger log = LoggerFactory.getLogger(RouteCatalog.class);

    private static final String RESOURCE = "/translink/routes.csv";

    private final Map<String, Route> byRouteId;

    public RouteCatalog() {
        this.byRouteId = load();
    }

    /** The route for a GTFS route_id, or empty when the catalog has no entry for it. */
    public Optional<Route> find(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRouteId.get(routeId));
    }

    private static Map<String, Route> load() {
        Map<String, Route> routes = new HashMap<>();
        try (InputStream in = RouteCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.error("Route catalog {} is missing; arrivals will have no route numbers", RESOURCE);
                return routes;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("route_id,")) {
                    continue;   // comment banner or header
                }
                // Safe to split naively: no route name in the feed contains a comma.
                String[] fields = line.split(",", 3);
                if (fields.length < 3) {
                    continue;
                }
                routes.put(fields[0], toRoute(fields[1], fields[2]));
            }
        } catch (Exception e) {
            log.error("Could not read route catalog {}: {}", RESOURCE, e.toString());
        }
        return routes;
    }

    /**
     * SkyTrain and SeaBus have no route number, so their name doubles as the label.
     * Bus numbers are zero-padded in GTFS ("002"), but riders read them as "2".
     */
    private static Route toRoute(String shortName, String longName) {
        String number = shortName.strip();
        String name = longName.strip();
        if (number.isEmpty()) {
            return new Route(name, "");
        }
        return new Route(number.replaceFirst("^0+(?=\\d)", "").toUpperCase(Locale.ENGLISH), name);
    }
}
