package project.group1.commutemate.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import project.group1.commutemate.model.RideMessage;

public interface RideMessageRepository extends JpaRepository<RideMessage, Long> {

    /** Initial page: newest messages first, reversed by the service for display. */
    List<RideMessage> findTop100ByRide_IdOrderByIdDesc(Long rideId);

    /** Polling cursor is ID-based, so results must also be ordered by ID. */
    List<RideMessage> findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(
            Long rideId, Long afterId);
}
