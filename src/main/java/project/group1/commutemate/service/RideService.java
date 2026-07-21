package project.group1.commutemate.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideLocations;
import project.group1.commutemate.repository.RideRepository;

/** Ride creation, lookup, search, and ownership. */
@Service
public class RideService {

    private final RideRepository rideRepository;
    private final Clock clock;

    public RideService(RideRepository rideRepository, Clock clock) {
        this.rideRepository = rideRepository;
        this.clock = clock;
    }

    public List<Ride> findAllUpcoming() {
        return rideRepository.findByDepartAtAfterOrderByDepartAtAsc(now());
    }

    public List<Ride> findUpcomingByDriverEmail(String driverEmail) {
        if (driverEmail == null || driverEmail.isBlank()) {
            return List.of();
        }
        return rideRepository.findByDriverEmailIgnoreCaseAndDepartAtAfterOrderByDepartAtAsc(
                driverEmail, now());
    }

    public Ride findById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideOperationException("Ride not found."));
    }

    public List<Ride> search(String query, String sort) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Ride> result = new ArrayList<>();
        for (Ride ride : findAllUpcoming()) {
            String haystack = (ride.getFrom() + " " + ride.getTo() + " " + ride.getDriver())
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(q)) {
                result.add(ride);
            }
        }
        result.sort(comparatorFor(sort));
        return result;
    }

    private Comparator<Ride> comparatorFor(String sort) {
        if (sort == null) {
            sort = "Departure";
        }
        return switch (sort) {
            case "Price" -> Comparator.comparingInt(Ride::getPrice);
            case "Eco-Score" -> Comparator.comparingInt(Ride::getEcoScore).reversed();
            case "Rating" -> Comparator.comparingDouble(Ride::getRating).reversed();
            default -> Comparator.comparing(Ride::getDepartAt);
        };
    }

    @Transactional
    public Ride create(String driverEmail, String driverName, String from, String to,
                       LocalDateTime departAt, int seats, int price, String notes) {
        validateOwner(driverEmail, driverName);
        String pickup = RideLocations.canonical(from);
        String destination = RideLocations.canonical(to);
        validateCreate(pickup, destination, departAt, seats, price);

        int points = seats * 8 + 5;
        int ecoScore = Math.min(95, 55 + seats * 8);
        Ride ride = new Ride(normalizeEmail(driverEmail), driverName.trim(), initialsOf(driverName),
                pickup, destination, departAt, seats, 0, price, points, ecoScore,
                "Your vehicle", 5.0, blankToNull(notes));
        return rideRepository.save(ride);
    }

    // Validates that a profile has driver capability
    private void validateOwner(String driverEmail, String driverName) {
        if (driverEmail == null || driverEmail.isBlank()
                || driverName == null || driverName.isBlank()) {
            throw new RideOperationException("A signed-in driver is required to create a ride.");
        }
    }

    // from/to are already canonical here: null means the value was missing or off the list
    private void validateCreate(String from, String to, LocalDateTime departAt, int seats, int price) {
        if (from == null || to == null) {
            throw new RideOperationException("Choose a pickup and destination from the list.");
        }
        if (from.equalsIgnoreCase(to)) {
            throw new RideOperationException("Pickup and destination must be different.");
        }
        if (departAt == null || !departAt.isAfter(now())) {
            throw new RideOperationException("Departure must be in the future.");
        }
        if (seats < 1 || seats > 5) {
            throw new RideOperationException("Seats must be between 1 and 5.");
        }
        if (price < 0 || price > 10) {
            throw new RideOperationException("Price must be between $0 and $10.");
        }
    }

    // Returns the current time
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.toString();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
