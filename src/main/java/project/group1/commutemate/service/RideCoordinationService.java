package project.group1.commutemate.service;

import java.time.Clock;
import java.time.Duration;
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

    // Deletes a ride and all requests
    @Transactional
    public void deleteOwnedRide(Long rideId, Profile driver) {
        requireDriverCapability(driver);
        Ride ride = lockedRide(rideId);
        requireRideOwner(ride, driver, "delete this ride");
        // Ride.requests uses cascade remove
        rideRepository.delete(ride);
    }

    // Epic 4: post-ride workflow, step 1 of 2. The rider confirms they are
    // physically boarding, shortly before departure. This does not complete
    // the ride by itself — the driver still has to confirm arrival for
    // everyone who boarded (see confirmArrival() below).
    @Transactional
    public RideRequest confirmBoarding(Long requestId, Profile rider) {
        requireRiderCapability(rider);
        RideRequest request = lockedRequest(requestId);
        Ride ride = request.getRide();

        if (!request.getRiderEmail().equalsIgnoreCase(rider.getEmail())) {
            throw new RideOperationException("Only the rider on this request can confirm boarding.");
        }
        if (request.getStatus() != RequestStatus.CONFIRMED) {
            throw new RideOperationException("Only a confirmed request can board.");
        }
        if (!isWithinBoardingWindow(ride)) {
            throw new RideOperationException(
                    "Boarding opens 30 minutes before departure.");
        }

        request.setStatus(RequestStatus.BOARDING_CONFIRMED);
        return requestRepository.save(request);
    }

    // Epic 4: post-ride workflow, step 2 of 2. The driver confirms arrival at
    // the destination, which completes the ride for every rider who boarded.
    // Reward points/eco-score for this ride become part of the driver's
    // total the moment at least one request is COMPLETED — see
    // RewardService.summaryForDriver(), which now counts a ride only once it
    // has an actual completed rider, not just because it was published.
    @Transactional
    public List<RideRequest> confirmArrival(Long rideId, Profile driver) {
        requireDriverCapability(driver);
        Ride ride = lockedRide(rideId);
        requireRideOwner(ride, driver, "confirm arrival for this ride");

        List<RideRequest> boarded = requestRepository
                .findByRide_IdAndStatus(rideId, RequestStatus.BOARDING_CONFIRMED);
        if (boarded.isEmpty()) {
            throw new RideOperationException(
                    "No riders have confirmed boarding yet, so this ride cannot be completed.");
        }

        for (RideRequest request : boarded) {
            request.setStatus(RequestStatus.COMPLETED);
        }
        return requestRepository.saveAll(boarded);
    }

    // A rider can confirm boarding starting 30 minutes before departure and
    // up until the ride departs. (Payment status and the rider-facing
    // "commute status" summary are follow-up items, not yet part of this
    // step — see team discussion.)
    private boolean isWithinBoardingWindow(Ride ride) {
        if (ride.getDepartAt() == null) {
            return false;
        }
        LocalDateTime windowStart = ride.getDepartAt().minus(Duration.ofMinutes(30));
        return !now().isBefore(windowStart);
    }

    // Finds all requests for a rider
    public List<RideRequest> findRequestsForRider(String riderEmail) {
        if (riderEmail == null || riderEmail.isBlank()) {
            return List.of();
        }
        return requestRepository.findByRiderEmailIgnoreCaseOrderByUpdatedAtDesc(riderEmail);
    }

    // Epic 4: rides that have at least one rider awaiting an arrival
    // confirmation, regardless of departure time — a ride can (and usually
    // will) have already departed by the time the driver clicks "Arrived".
    public List<Ride> findRidesAwaitingArrival(String driverEmail) {
        if (driverEmail == null || driverEmail.isBlank()) {
            return List.of();
        }
        return requestRepository
                .findByRide_DriverEmailIgnoreCaseAndStatus(driverEmail, RequestStatus.BOARDING_CONFIRMED)
                .stream()
                .map(RideRequest::getRide)
                .distinct()
                .toList();
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
