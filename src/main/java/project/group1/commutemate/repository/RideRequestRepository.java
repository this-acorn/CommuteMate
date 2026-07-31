package project.group1.commutemate.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.RideRequest;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    // Finds a ride request by the ride id and rider email, ignoring case
    Optional<RideRequest> findByRideIdAndRiderEmailIgnoreCase(Long rideId, String riderEmail);

    // Finds all ride requests for a specific rider ordered by the last updated time 
    @EntityGraph(attributePaths = "ride")
    List<RideRequest> findByRiderEmailIgnoreCaseOrderByUpdatedAtDesc(String riderEmail);

    // Finds all ride requests for rides driven by a specific driver ordered by the last updated time 
    @EntityGraph(attributePaths = "ride")
    List<RideRequest> findByRide_DriverEmailIgnoreCaseOrderByUpdatedAtDesc(String driverEmail);

    // Epic 4: finds a driver's requests waiting on an arrival confirmation.
    // Needed because the ride may have already departed by the time the
    // driver clicks "Arrived", so it can no longer be found via an
    // upcoming-only ride lookup.
    @EntityGraph(attributePaths = "ride")
    List<RideRequest> findByRide_DriverEmailIgnoreCaseAndStatus(String driverEmail, RequestStatus status);

    // Finds a ride request by its id and locks it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RideRequest request join fetch request.ride where request.id = :id")
    Optional<RideRequest> findByIdForUpdate(@Param("id") Long id);

    // Epic 4: finds all requests for a ride in a given status, e.g. everyone
    // who confirmed boarding, so the driver's arrival can complete them all.
    List<RideRequest> findByRide_IdAndStatus(Long rideId, RequestStatus status);
}
