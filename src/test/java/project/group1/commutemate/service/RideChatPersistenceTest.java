package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideRequest;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.repository.RideMessageRepository;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

@SpringBootTest
@Transactional
class RideChatPersistenceTest {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private RideRequestRepository requestRepository;

    @Autowired
    private RideMessageRepository messageRepository;

    @Autowired
    private RideChatService chatService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void confirmedRiderMessagePersistsAndRideDeletionRemovesConversation() {
        Ride ride = rideRepository.save(new Ride(
                "driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU Burnaby",
                LocalDateTime.now().plusDays(2), 3, 1, 4, 20, 80,
                "Test car", 5.0, null));
        RideRequest request = new RideRequest(ride, "rider@sfu.ca", "Demo Rider");
        request.setStatus(RequestStatus.CONFIRMED);
        requestRepository.save(request);

        Profile rider = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
        chatService.sendMessage(ride.getId(), rider, "I will be at the pickup point.");
        messageRepository.flush();
        entityManager.clear();

        RideChatView chat = chatService.openChat(ride.getId(), rider);
        assertEquals(1, chat.messages().size());
        assertEquals("I will be at the pickup point.", chat.messages().getFirst().body());
        assertTrue(chat.messages().getFirst().mine());

        Ride persistedRide = rideRepository.findById(ride.getId()).orElseThrow();
        rideRepository.delete(persistedRide);
        rideRepository.flush();

        assertTrue(messageRepository
                .findTop100ByRide_IdOrderByIdDesc(ride.getId()).isEmpty());
        assertTrue(requestRepository
                .findByRideIdAndRiderEmailIgnoreCase(ride.getId(), "rider@sfu.ca").isEmpty());

        entityManager.clear();
        RideChatNotFoundException error = assertThrows(RideChatNotFoundException.class,
                () -> chatService.openChat(ride.getId(), rider));
        assertEquals("Ride not found.", error.getMessage());
    }
}
