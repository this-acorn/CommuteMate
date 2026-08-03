package project.group1.commutemate.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import project.group1.commutemate.model.RideChatReadState;

public interface RideChatReadStateRepository
        extends JpaRepository<RideChatReadState, Long> {

    Optional<RideChatReadState> findByRide_IdAndReaderEmailIgnoreCase(
            Long rideId, String readerEmail);

    List<RideChatReadState> findByRide_IdInAndReaderEmailIgnoreCase(
            Collection<Long> rideIds, String readerEmail);
}
