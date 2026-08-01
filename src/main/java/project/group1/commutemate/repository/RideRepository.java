package project.group1.commutemate.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import project.group1.commutemate.model.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {

    // Finds all rides that depart after the given time, ordered by departure time ascending
    List<Ride> findByDepartAtAfterOrderByDepartAtAsc(LocalDateTime now);

    // Finds all rides for a specific driver that depart after the given time, ordered by departure time ascending
    List<Ride> findByDriverEmailIgnoreCaseAndDepartAtAfterOrderByDepartAtAsc(
            String driverEmail, LocalDateTime now);

    // All rides regardless of time (for reward totals). JOIN FETCH avoids N+1.
    @Query("select distinct r from Ride r left join fetch r.requests where lower(r.driverEmail) = lower(:driverEmail)")
    List<Ride> findByDriverEmailIgnoreCase(@Param("driverEmail") String driverEmail);

    // Checks if there are any rides that depart after the given time
    boolean existsByDepartAtAfter(LocalDateTime now);

    // Finds a ride by its id
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);
}