package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.exception.RideOperationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideMessage;
import project.group1.commutemate.model.Role;
import project.group1.commutemate.repository.RideMessageRepository;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

@ExtendWith(MockitoExtension.class)
class RideChatServiceTest {

    private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-23T08:00:00Z"), VANCOUVER);

    @Mock
    private RideRepository rideRepository;

    @Mock
    private RideRequestRepository requestRepository;

    @Mock
    private RideMessageRepository messageRepository;

    private RideChatService service;
    private Ride ride;
    private Profile driver;
    private Profile rider;

    @BeforeEach
    void setUp() {
        service = new RideChatService(
                rideRepository, requestRepository, messageRepository, CLOCK);
        ride = new Ride("driver@sfu.ca", "Demo Driver", "DD", "Metrotown", "SFU",
                LocalDateTime.now(CLOCK).plusDays(1), 3, 1, 4, 20, 80,
                "Test car", 5.0, null);
        ride.setId(10L);
        driver = new Profile("driver@sfu.ca", "Demo Driver", Role.DRIVER, 0, 0);
        rider = new Profile("rider@sfu.ca", "Demo Rider", Role.RIDER, 0, 0);
    }

    @Test
    void ownerCanOpenRideChatWithoutARequest() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(messageRepository.findByRide_IdOrderByCreatedAtAscIdAsc(10L))
                .thenReturn(List.of());

        RideChatView chat = service.openChat(10L, driver);

        assertTrue(chat.owner());
        assertTrue(chat.messages().isEmpty());
        verifyNoInteractions(requestRepository);
    }

    @Test
    void confirmedRiderCanOpenRideChat() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
                10L, "rider@sfu.ca", RequestStatus.CONFIRMED)).thenReturn(true);
        when(messageRepository.findByRide_IdOrderByCreatedAtAscIdAsc(10L))
                .thenReturn(List.of(new RideMessage(
                        ride, "driver@sfu.ca", "Demo Driver", "Meet at the east entrance.")));

        RideChatView chat = service.openChat(10L, rider);

        assertFalse(chat.owner());
        assertEquals(1, chat.messages().size());
        assertEquals("Meet at the east entrance.", chat.messages().getFirst().body());
        assertFalse(chat.messages().getFirst().mine());
    }

    @Test
    void pendingOrUnrelatedRiderCannotOpenChat() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
                10L, "rider@sfu.ca", RequestStatus.CONFIRMED)).thenReturn(false);

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.openChat(10L, rider));

        assertEquals("Only the ride owner and confirmed riders can access this chat.",
                error.getMessage());
        verify(messageRepository, never()).findByRide_IdOrderByCreatedAtAscIdAsc(10L);
    }

    @Test
    void sendMessageUsesSignedInIdentityAndTrimsBody() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
                10L, "RIDER@SFU.CA", RequestStatus.CONFIRMED)).thenReturn(true);
        when(messageRepository.save(any(RideMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Profile signedInRider = new Profile(
                "  RIDER@SFU.CA  ", "  Demo Rider  ", Role.RIDER, 0, 0);

        RideMessage saved = service.sendMessage(
                10L, signedInRider, "  I am waiting by the library.  ");

        assertEquals("rider@sfu.ca", saved.getSenderEmail());
        assertEquals("Demo Rider", saved.getSenderName());
        assertEquals("I am waiting by the library.", saved.getBody());
        assertEquals(ride, saved.getRide());
    }

    @Test
    void blankMessageIsRejectedWithoutSaving() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.sendMessage(10L, driver, "   \n  "));

        assertEquals("Message cannot be empty.", error.getMessage());
        verify(messageRepository, never()).save(any(RideMessage.class));
    }

    @Test
    void overlongMessageIsRejectedWithoutSaving() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        String body = "x".repeat(RideChatService.MAX_MESSAGE_LENGTH + 1);

        RideOperationException error = assertThrows(RideOperationException.class,
                () -> service.sendMessage(10L, driver, body));

        assertEquals("Message cannot be longer than 1000 characters.", error.getMessage());
        verify(messageRepository, never()).save(any(RideMessage.class));
    }

    @Test
    void confirmedRiderMessageIsMarkedAsMineWhenChatReloads() {
        RideMessage message = new RideMessage(
                ride, "rider@sfu.ca", "Demo Rider", "On my way.");
        message.setId(50L);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
                10L, "rider@sfu.ca", RequestStatus.CONFIRMED)).thenReturn(true);
        when(messageRepository.findByRide_IdOrderByCreatedAtAscIdAsc(10L))
                .thenReturn(List.of(message));

        RideChatView chat = service.openChat(10L, rider);

        assertTrue(chat.messages().getFirst().mine());
        ArgumentCaptor<Long> rideId = ArgumentCaptor.forClass(Long.class);
        verify(messageRepository).findByRide_IdOrderByCreatedAtAscIdAsc(rideId.capture());
        assertEquals(10L, rideId.getValue());
    }
}
