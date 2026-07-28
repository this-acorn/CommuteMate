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

    // Checks whether a rider has a request with the required status for this ride
    boolean existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
            Long rideId, String riderEmail, RequestStatus status);

    // Finds all ride requests for a specific rider ordered by the last updated time 
    @EntityGraph(attributePaths = "ride")
    List<RideRequest> findByRiderEmailIgnoreCaseOrderByUpdatedAtDesc(String riderEmail);

    // Finds all ride requests for rides driven by a specific driver ordered by the last updated time 
    @EntityGraph(attributePaths = "ride")
    List<RideRequest> findByRide_DriverEmailIgnoreCaseOrderByUpdatedAtDesc(String driverEmail);

    // Finds a ride request by its id and locks it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RideRequest request join fetch request.ride where request.id = :id")
    Optional<RideRequest> findByIdForUpdate(@Param("id") Long id);
}
