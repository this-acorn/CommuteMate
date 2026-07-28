package project.group1.commutemate.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

/** Business rules for rider requests and driver decisions. */
@Service
public class RideCoordinationService {

    private final RideRepository rideRepository;
    private final RideRequestRepository requestRepository;
    private final Clock clock;

    public RideCoordinationService(RideRepository rideRepository,
                                   RideRequestRepository requestRepository,
                                   Clock clock) {
        this.rideRepository = rideRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Transactional
    public RideRequest requestSeat(Long rideId, Profile rider) {
        requireRiderCapability(rider);

        Ride ride = lockedRide(rideId);
        requireUpcomingRide(ride);
        if (ride.getDriverEmail().equalsIgnoreCase(rider.getEmail())) {
            throw new RideOperationException("You cannot request your own ride.");
        }
        if (ride.isFull()) {
            throw new RideOperationException("This ride is full.");
        }

        String normalizedEmail = rider.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedName = rider.getFullName().trim();

        Optional<RideRequest> existing = requestRepository
                .findByRideIdAndRiderEmailIgnoreCase(rideId, normalizedEmail);
        if (existing.isPresent()) {
            RideRequest request = existing.get();
            if (request.getStatus() == RequestStatus.CANCELLED
                    || request.getStatus() == RequestStatus.REJECTED) {
                request.setStatus(RequestStatus.PENDING);
                request.setRiderEmail(normalizedEmail);
                request.setRiderName(normalizedName);
                return requestRepository.save(request);
            }
            throw new RideOperationException("You already requested this ride.");
        }

        return requestRepository.save(new RideRequest(
                ride,
                normalizedEmail,
                normalizedName));
    }

    // Confirms a request and reserves a seat
    @Transactional
    public RideRequest confirmRequest(Long requestId, Profile driver) {
        requireDriverCapability(driver);
        RideRequest request = lockedRequest(requestId);
        Ride ride = lockedRide(request.getRide().getId());

        requireRideOwner(ride, driver, "manage this request");
        requireUpcomingRide(ride);
        requirePending(request);
        if (ride.isFull()) {
            throw new RideOperationException("This ride is full. The request was not confirmed.");
        }

        request.setStatus(RequestStatus.CONFIRMED);
        ride.reserveSeat();
        rideRepository.save(ride);
        return requestRepository.save(request);
    }

    // Rejects a request without reserving a seat
    @Transactional
    public RideRequest rejectRequest(Long requestId, Profile driver) {
        requireDriverCapability(driver);
        RideRequest request = lockedRequest(requestId);
        Ride ride = lockedRide(request.getRide().getId());

        requireRideOwner(ride, driver, "manage this request");
        requireUpcomingRide(ride);
        requirePending(request);
        request.setStatus(RequestStatus.REJECTED);
        return requestRepository.save(request);
    }

    // Cancels a request and releases a reserved seat
    @Transactional
    public RideRequest cancelRequest(Long requestId, Profile rider) {
        requireRiderCapability(rider, "Your account cannot cancel rider requests.");

        RideRequest request = lockedRequest(requestId);
        if (!request.getRiderEmail().equalsIgnoreCase(rider.getEmail())) {
            throw new RideOperationException("You can only cancel your own request.");
        }
        if (request.getStatus() != RequestStatus.PENDING
                && request.getStatus() != RequestStatus.CONFIRMED) {
            throw new RideOperationException("Only pending or confirmed requests can be cancelled.");
        }

        Ride ride = lockedRide(request.getRide().getId());
        requireUpcomingRide(ride);

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            try {
                ride.releaseSeat();
            } catch (IllegalStateException ex) {
                throw new RideOperationException(
                        "Seat count is inconsistent. The request was not cancelled.");
            }
            rideRepository.save(ride);
        }

        request.setStatus(RequestStatus.CANCELLED);
        return requestRepository.save(request);
    }

    // Deletes a ride together with all requests and chat messages
    @Transactional
    public void deleteOwnedRide(Long rideId, Profile driver) {
        requireDriverCapability(driver);
        Ride ride = lockedRide(rideId);
        requireRideOwner(ride, driver, "delete this ride");
        // Ride requests and messages both use cascade remove
        rideRepository.delete(ride);
    }
    // Finds all requests for a rider
    public List<RideRequest> findRequestsForRider(String riderEmail) {
        if (riderEmail == null || riderEmail.isBlank()) {
            return List.of();
        }
        return requestRepository.findByRiderEmailIgnoreCaseOrderByUpdatedAtDesc(riderEmail);
    }

    // Finds all requests for a driver
    public List<RideRequest> findRequestsForDriver(String driverEmail) {
        if (driverEmail == null || driverEmail.isBlank()) {
            return List.of();
        }
        return requestRepository.findByRide_DriverEmailIgnoreCaseOrderByUpdatedAtDesc(driverEmail);
    }

    // Finds a request for a rider by ride id
    public Optional<RideRequest> findRequestForRider(Long rideId, String riderEmail) {
        if (riderEmail == null || riderEmail.isBlank()) {
            return Optional.empty();
        }
        return requestRepository.findByRideIdAndRiderEmailIgnoreCase(rideId, riderEmail);
    }

    // Locks a ride for update and returns it
    private Ride lockedRide(Long rideId) {
        return rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideOperationException("Ride not found."));
    }

    // Locks a request for update and returns it
    private RideRequest lockedRequest(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RideOperationException("Ride request not found."));
    }

    // Validates that a profile has rider capability
    private void requireRiderCapability(Profile rider) {
        requireRiderCapability(rider, "Your account cannot request rides.");
    }

    private void requireRiderCapability(Profile rider, String message) {
        if (rider == null || !rider.isRiderCapable()) {
            throw new RideOperationException(message);
        }
    }

    private void requireDriverCapability(Profile driver) {
        if (driver == null || !driver.isDriverCapable()) {
            throw new RideOperationException("Your account cannot manage driver requests.");
        }
    }

    // Validates that a profile is the owner of a ride
    private void requireRideOwner(Ride ride, Profile driver, String action) {
        if (!ride.getDriverEmail().equalsIgnoreCase(driver.getEmail())) {
            throw new RideOperationException("Only the ride owner can " + action + ".");
        }
    }

    // Validates that a ride is upcoming 
    private void requireUpcomingRide(Ride ride) {
        if (ride.getDepartAt() == null || !ride.getDepartAt().isAfter(now())) {
            throw new RideOperationException("This ride has already departed.");
        }
    }

    // Validates that a request is pending
    private void requirePending(RideRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RideOperationException("This request is no longer pending.");
        }
    }

    // Returns the current time
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
