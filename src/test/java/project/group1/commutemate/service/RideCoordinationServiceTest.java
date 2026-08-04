package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

@ExtendWith(MockitoExtension.class)
class RideCoordinationServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T19:00:00Z"), VANCOUVER);

    @Mock
    private RideRepository rideRepository;

    @Mock
    private RideRequestRepository requestRepository;

    private RideCoordinationService service;
    private Ride ride;
    private Profile rider;

    @BeforeEach
    void setUp() {
        service = new RideCoordinationService(rideRepository, requestRepository, CLOCK);
        ride = new Ride("driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU",
                LocalDateTime.now(CLOCK).plusDays(1), 3, 0, 4, 20, 80,
                "Test car", 5.0, null);
        ride.setId(10L);
        rider = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
    }

    @Test
    void requestSeatCreatesPendingRequestWithoutTakingSeat() {
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRideIdAndRiderEmailIgnoreCase(10L, "rider@sfu.ca"))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(RideRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RideRequest created = service.requestSeat(10L, rider);

        assertEquals(RequestStatus.PENDING, created.getStatus());
        assertEquals(0, ride.getSeatsTaken());
        verify(requestRepository).save(created);
    }

    @Test
    void requestSeatRejectsDuplicateRequest() {
        RideRequest existing = new RideRequest(ride, rider.getEmail(), rider.getFullName());
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRideIdAndRiderEmailIgnoreCase(10L, rider.getEmail()))
                .thenReturn(Optional.of(existing));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.requestSeat(10L, rider));

        assertEquals("You already requested this ride.", error.getMessage());
    }

    @Test
    void requestSeatRejectsRideOwner() {
        Profile owner = new Profile("driver@sfu.ca", "Demo Driver", Role.RIDER, 0, 0);
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.requestSeat(10L, owner));

        assertEquals("You cannot request your own ride.", error.getMessage());
    }

    @Test
    void requestSeatRejectsFullRide() {
        ride.setSeatsTaken(ride.getSeats());
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.requestSeat(10L, rider));

        assertEquals("This ride is full.", error.getMessage());
        assertEquals(ride.getSeats(), ride.getSeatsTaken());
        verify(requestRepository, never()).save(any(RideRequest.class));
    }

    @Test
    void requestSeatRejectsDepartedRide() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).minusMinutes(1));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.requestSeat(10L, rider));

        assertEquals("This ride has already departed.", error.getMessage());
        assertEquals(0, ride.getSeatsTaken());
        verify(requestRepository, never()).save(any(RideRequest.class));
    }

    @Test
    void cancelledRequestCanBeSubmittedAgainAndRefreshesRiderIdentity() {
        Profile updatedRider = new Profile(
                "  RIDER@SFU.CA  ", "  Updated Rider  ", Role.RIDER, 0, 0);
        RideRequest existing = new RideRequest(ride, "RIDER@SFU.CA", "Old Rider Name");
        existing.setStatus(RequestStatus.CANCELLED);
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRideIdAndRiderEmailIgnoreCase(10L, "rider@sfu.ca"))
                .thenReturn(Optional.of(existing));
        when(requestRepository.save(existing)).thenReturn(existing);

        RideRequest reopened = service.requestSeat(10L, updatedRider);

        assertEquals(RequestStatus.PENDING, reopened.getStatus());
        assertEquals("rider@sfu.ca", reopened.getRiderEmail());
        assertEquals("Updated Rider", reopened.getRiderName());
        verify(requestRepository).save(existing);
    }

    @Test
    void rejectedRequestCanBeSubmittedAgainAndRefreshesRiderIdentity() {
        Profile updatedRider = new Profile(
                "  RIDER@SFU.CA  ", "  Updated Rider  ", Role.RIDER, 0, 0);
        RideRequest existing = new RideRequest(ride, "RIDER@SFU.CA", "Old Rider Name");
        existing.setStatus(RequestStatus.REJECTED);
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRideIdAndRiderEmailIgnoreCase(10L, "rider@sfu.ca"))
                .thenReturn(Optional.of(existing));
        when(requestRepository.save(existing)).thenReturn(existing);

        RideRequest reopened = service.requestSeat(10L, updatedRider);

        assertEquals(RequestStatus.PENDING, reopened.getStatus());
        assertEquals("rider@sfu.ca", reopened.getRiderEmail());
        assertEquals("Updated Rider", reopened.getRiderName());
        verify(requestRepository).save(existing);
    }

    @Test
    void confirmRequestReservesExactlyOneSeat() {
        RideRequest request = request(20L);
        Profile driver = driver("driver@sfu.ca");
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.save(request)).thenReturn(request);
        when(rideRepository.save(ride)).thenReturn(ride);

        RideRequest confirmed = service.confirmRequest(20L, driver);

        assertEquals(RequestStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(1, ride.getSeatsTaken());
        verify(rideRepository).save(ride);
    }

    @Test
    void confirmRequestRejectsNonOwner() {
        RideRequest request = request(20L);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmRequest(20L, driver("other@sfu.ca")));

        assertEquals("Only the ride owner can manage this request.", error.getMessage());
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(0, ride.getSeatsTaken());
    }

    @Test
    void confirmRequestDoesNotOverbookFullRide() {
        ride.setSeatsTaken(ride.getSeats());
        RideRequest request = request(20L);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmRequest(20L, driver("driver@sfu.ca")));

        assertEquals("This ride is full. The request was not confirmed.", error.getMessage());
        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertEquals(ride.getSeats(), ride.getSeatsTaken());
    }

    @Test
    void confirmRequestRejectsDepartedRide() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).minusMinutes(1));
        RideRequest request = request(20L);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmRequest(20L, driver("driver@sfu.ca")));

        assertEquals("This ride has already departed.", error.getMessage());
        assertEquals(RequestStatus.PENDING, request.getStatus());
    }

    @Test
    void rejectRequestDoesNotTakeSeat() {
        RideRequest request = request(20L);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.save(request)).thenReturn(request);

        RideRequest rejected = service.rejectRequest(20L, driver("driver@sfu.ca"));

        assertEquals(RequestStatus.REJECTED, rejected.getStatus());
        assertEquals(0, ride.getSeatsTaken());
    }

    @Test
    void rejectRequestBlocksNonPendingRequest() {
        RideRequest request = request(20L);
        request.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.rejectRequest(20L, driver("driver@sfu.ca")));

        assertEquals("This request is no longer pending.", error.getMessage());
        assertEquals(RequestStatus.CONFIRMED, request.getStatus());
        assertEquals(0, ride.getSeatsTaken());
        verify(requestRepository, never()).save(any(RideRequest.class));
    }

    @Test
    void cancelPendingRequestDoesNotChangeSeatCount() {
        RideRequest request = request(30L);
        when(requestRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.save(request)).thenReturn(request);

        RideRequest cancelled = service.cancelRequest(30L, rider);

        assertEquals(RequestStatus.CANCELLED, cancelled.getStatus());
        assertEquals(0, ride.getSeatsTaken());
    }

    @Test
    void cancelConfirmedRequestReleasesOneSeat() {
        ride.setSeatsTaken(2);
        RideRequest request = request(30L);
        request.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.save(request)).thenReturn(request);
        when(rideRepository.save(ride)).thenReturn(ride);

        RideRequest cancelled = service.cancelRequest(30L, rider);

        assertEquals(RequestStatus.CANCELLED, cancelled.getStatus());
        assertEquals(1, ride.getSeatsTaken());
    }

    @Test
    void cancelConfirmedRequestRejectsInconsistentSeatCount() {
        RideRequest request = request(30L);
        request.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(request));
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.cancelRequest(30L, rider));

        assertEquals("Seat count is inconsistent. The request was not cancelled.", error.getMessage());
        assertEquals(RequestStatus.CONFIRMED, request.getStatus());
    }

    @Test
    void cancelRequestRejectsAnotherRider() {
        RideRequest request = request(30L);
        Profile other = new Profile("other@sfu.ca", "Other Rider", Role.RIDER, 0, 0);
        when(requestRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(request));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.cancelRequest(30L, other));

        assertEquals("You can only cancel your own request.", error.getMessage());
        assertEquals(RequestStatus.PENDING, request.getStatus());
    }

    @Test
    void deleteOwnedRideDelegatesDeletionToRideRepository() {
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        service.deleteOwnedRide(10L, driver("driver@sfu.ca"));

        verify(rideRepository).delete(ride);
        verifyNoInteractions(requestRepository);
    }

    @Test
    void deleteOwnedRideRejectsNonOwner() {
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.deleteOwnedRide(10L, driver("other@sfu.ca")));

        assertEquals("Only the ride owner can delete this ride.", error.getMessage());
    }

    // --- Epic 4: post-ride workflow ---

    @Test
    void confirmBoardingMarksRequestBoardingConfirmed_withinThirtyMinuteWindow() {
        // Ride departs 20 minutes from now — inside the 30-minute window
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(20));
        RideRequest confirmed = request(1L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));
        when(requestRepository.save(confirmed)).thenReturn(confirmed);

        RideRequest result = service.confirmBoarding(1L, rider);

        assertEquals(RequestStatus.BOARDING_CONFIRMED, result.getStatus());
    }

    @Test
    void confirmBoardingRejectsOutsideThirtyMinuteWindow() {
        // Ride departs in 2 hours — too early to board
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusHours(2));
        RideRequest confirmed = request(1L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmBoarding(1L, rider));

        assertEquals("Boarding opens 30 minutes before departure.", error.getMessage());
    }

    // Boundary tests for the 30-minute window itself, not just "clearly
    // inside" / "clearly outside" cases.
    @Test
    void confirmBoardingAllowsExactlyAtThirtyMinuteBoundary() {
        // Ride departs in exactly 30 minutes — the boundary is inclusive
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(30));
        RideRequest confirmed = request(1L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));
        when(requestRepository.save(confirmed)).thenReturn(confirmed);

        RideRequest result = service.confirmBoarding(1L, rider);

        assertEquals(RequestStatus.BOARDING_CONFIRMED, result.getStatus());
    }

    @Test
    void confirmBoardingRejectsOneMinuteOutsideThirtyMinuteBoundary() {
        // Ride departs in 30 minutes and 1 second — just outside the window
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(30).plusSeconds(1));
        RideRequest confirmed = request(1L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmBoarding(1L, rider));

        assertEquals("Boarding opens 30 minutes before departure.", error.getMessage());
    }

    @Test
    void confirmBoardingRejectsNonPendingConfirmedRequest() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(10));
        RideRequest pending = request(1L); // default status is PENDING
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmBoarding(1L, rider));

        assertEquals("Only a confirmed request can board.", error.getMessage());
    }

    @Test
    void confirmBoardingRejectsWrongRider() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(10));
        RideRequest confirmed = request(1L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));

        Profile someoneElse = new Profile("someone-else@sfu.ca", "Someone Else", Role.RIDER, 0, 0);

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmBoarding(1L, someoneElse));

        assertEquals("Only the rider on this request can confirm boarding.", error.getMessage());
    }

    @Test
    void confirmArrivalCompletesEveryBoardedRequest() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).minusMinutes(5)); // already departed
        RideRequest boarded1 = request(1L);
        boarded1.setStatus(RequestStatus.BOARDING_CONFIRMED);
        RideRequest boarded2 = request(2L);
        boarded2.setStatus(RequestStatus.BOARDING_CONFIRMED);

        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRide_IdAndStatus(10L, RequestStatus.BOARDING_CONFIRMED))
                .thenReturn(java.util.List.of(boarded1, boarded2));
        when(requestRepository.saveAll(java.util.List.of(boarded1, boarded2)))
                .thenReturn(java.util.List.of(boarded1, boarded2));

        var completed = service.confirmArrival(10L, driver("driver@sfu.ca"));

        assertEquals(2, completed.size());
        assertEquals(RequestStatus.COMPLETED, boarded1.getStatus());
        assertEquals(RequestStatus.COMPLETED, boarded2.getStatus());
    }

    @Test
    void confirmArrivalRejectsWhenNobodyBoarded() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).minusMinutes(5)); // already departed
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRide_IdAndStatus(10L, RequestStatus.BOARDING_CONFIRMED))
                .thenReturn(java.util.List.of());

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmArrival(10L, driver("driver@sfu.ca")));

        assertEquals("No riders have confirmed boarding yet, so this ride cannot be completed.",
                error.getMessage());
    }

    // confirmArrival() didn't check the departure time at all — a driver
    // could complete the ride right after boarding was confirmed, well
    // before departure actually happened.
    @Test
    void confirmArrivalRejectsBeforeScheduledDeparture() {
        ride.setDepartAt(LocalDateTime.now(CLOCK).plusMinutes(20)); // hasn't departed yet
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmArrival(10L, driver("driver@sfu.ca")));

        assertEquals("You can't confirm arrival before the scheduled departure time.",
                error.getMessage());
    }

    @Test
    void confirmArrivalAllowsExactlyAtDepartureTime() {
        ride.setDepartAt(LocalDateTime.now(CLOCK)); // departing this instant
        RideRequest boarded = request(1L);
        boarded.setStatus(RequestStatus.BOARDING_CONFIRMED);
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.findByRide_IdAndStatus(10L, RequestStatus.BOARDING_CONFIRMED))
                .thenReturn(java.util.List.of(boarded));
        when(requestRepository.saveAll(java.util.List.of(boarded)))
                .thenReturn(java.util.List.of(boarded));

        var completed = service.confirmArrival(10L, driver("driver@sfu.ca"));

        assertEquals(1, completed.size());
        assertEquals(RequestStatus.COMPLETED, boarded.getStatus());
    }

    @Test
    void confirmArrivalRejectsNonOwner() {
        when(rideRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.confirmArrival(10L, driver("other@sfu.ca")));

        assertEquals("Only the ride owner can confirm arrival for this ride.", error.getMessage());
    }

    private RideRequest request(Long id) {
        RideRequest request = new RideRequest(ride, rider.getEmail(), rider.getFullName());
        request.setId(id);
        return request;
    }

    private Profile driver(String email) {
        return new Profile(email, "Demo Driver", Role.DRIVER, 0, 0);
    }
}