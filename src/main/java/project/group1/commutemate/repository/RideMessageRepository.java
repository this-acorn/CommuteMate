package project.group1.commutemate.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import project.group1.commutemate.model.RideMessage;

public interface RideMessageRepository extends JpaRepository<RideMessage, Long> {

    List<RideMessage> findByRide_IdOrderByCreatedAtAscIdAsc(Long rideId);
}
