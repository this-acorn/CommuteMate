package project.group1.commutemate.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import project.group1.commutemate.model.RideMessage;

public interface RideMessageRepository extends JpaRepository<RideMessage, Long> {

    /** Initial page: newest messages first, reversed by the service for display. */
    List<RideMessage> findTop100ByRide_IdOrderByIdDesc(Long rideId);

    /** Polling cursor is ID-based, so results must also be ordered by ID. */
    List<RideMessage> findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(
            Long rideId, Long afterId);

    @Query("""
            select new project.group1.commutemate.repository.RideLatestMessageId(
                    message.ride.id, max(message.id))
            from RideMessage message
            where message.ride.id in :rideIds
              and lower(message.senderEmail) <> lower(:readerEmail)
            group by message.ride.id
            """)
    List<RideLatestMessageId> findLatestOtherMessageIds(
            @Param("rideIds") Collection<Long> rideIds,
            @Param("readerEmail") String readerEmail);
}
