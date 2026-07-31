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

    // Epic 4: all of a driver's rides regardless of time, so completed
    // (already-departed) rides still count toward their reward total —
    // findByDriverEmailIgnoreCaseAndDepartAtAfterOrderByDepartAtAsc above
    // would drop a ride from the total the moment it departs.
    List<Ride> findByDriverEmailIgnoreCase(String driverEmail);

    // Checks if there are any rides that depart after the given time
    boolean existsByDepartAtAfter(LocalDateTime now);

    // Finds a ride by its id
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);
}
