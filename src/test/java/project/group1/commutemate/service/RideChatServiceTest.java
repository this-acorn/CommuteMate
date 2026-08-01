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

import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.exception.RideChatValidationException;
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
    private static final List<RequestStatus> CHAT_ACCESS_STATUSES = List.of(
            RequestStatus.CONFIRMED,
            RequestStatus.BOARDING_CONFIRMED,
            RequestStatus.COMPLETED);

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
        when(messageRepository.findTop100ByRide_IdOrderByIdDesc(10L))
                .thenReturn(List.of());

        RideChatView chat = service.openChat(10L, driver);

        assertTrue(chat.owner());
        assertTrue(chat.messages().isEmpty());
        verifyNoInteractions(requestRepository);
    }

    @Test
    void initialMessagesAreDisplayedInAscendingIdOrder() {
        RideMessage newest = new RideMessage(
                ride, "driver@sfu.ca", "Demo Driver", "Second message");
        newest.setId(12L);
        RideMessage older = new RideMessage(
                ride, "driver@sfu.ca", "Demo Driver", "First message");
        older.setId(11L);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(messageRepository.findTop100ByRide_IdOrderByIdDesc(10L))
                .thenReturn(List.of(newest, older));

        RideChatView chat = service.openChat(10L, driver);

        assertEquals(List.of(11L, 12L),
                chat.messages().stream().map(message -> message.id()).toList());
    }

    @Test
    void missingRideUsesNotFoundFailure() {
        when(rideRepository.findById(99L)).thenReturn(Optional.empty());

        RideChatNotFoundException error = assertThrows(RideChatNotFoundException.class,
                () -> service.openChat(99L, driver));

        assertEquals("Ride not found.", error.getMessage());
        verifyNoInteractions(requestRepository, messageRepository);
    }

    @Test
    void confirmedRiderCanOpenRideChat() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(true);
        when(messageRepository.findTop100ByRide_IdOrderByIdDesc(10L))
                .thenReturn(List.of(new RideMessage(
                        ride, "driver@sfu.ca", "Demo Driver", "Meet at the east entrance.")));

        RideChatView chat = service.openChat(10L, rider);

        assertFalse(chat.owner());
        assertEquals(1, chat.messages().size());
        assertEquals("Meet at the east entrance.", chat.messages().getFirst().body());
        assertFalse(chat.messages().getFirst().mine());
    }

    @Test
    void pendingRiderCannotOpenChat() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(false);

        RideChatAccessException error = assertThrows(RideChatAccessException.class,
                () -> service.openChat(10L, rider));

        assertEquals("Only the ride owner and confirmed riders can access this chat.",
                error.getMessage());
        verify(messageRepository, never()).findTop100ByRide_IdOrderByIdDesc(10L);
    }

    @Test
    void unrelatedMemberCannotOpenChatOrReadMessages() {
        Profile unrelatedMember = new Profile(
                "outsider@sfu.ca", "Outside Member", Role.RIDER, 0, 0);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "outsider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(false);

        RideChatAccessException error = assertThrows(RideChatAccessException.class,
                () -> service.openChat(10L, unrelatedMember));

        assertEquals("Only the ride owner and confirmed riders can access this chat.",
                error.getMessage());
        verify(messageRepository, never()).findTop100ByRide_IdOrderByIdDesc(10L);
    }

    @Test
    void confirmedRiderCanSendMessageUnderAuthenticatedIdentity() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(true);
        when(messageRepository.save(any(RideMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RideMessage saved = service.sendMessage(
                10L, rider, "I am waiting at the east entrance.");

        assertEquals("rider@sfu.ca", saved.getSenderEmail());
        assertEquals("Demo Rider", saved.getSenderName());
        assertEquals("I am waiting at the east entrance.", saved.getBody());
        assertEquals(ride, saved.getRide());
        assertTrue(ride.getMessages().contains(saved));
    }

    @Test
    void messageBodyIsTrimmedBeforeSaving() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "RIDER@SFU.CA", CHAT_ACCESS_STATUSES)).thenReturn(true);
        when(messageRepository.save(any(RideMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Profile signedInRider = new Profile(
                "  RIDER@SFU.CA  ", "  Demo Rider  ", Role.RIDER, 0, 0);

        RideMessage saved = service.sendMessage(
                10L, signedInRider, "  I will arrive at 8:10 AM.  ");

        assertEquals("rider@sfu.ca", saved.getSenderEmail());
        assertEquals("Demo Rider", saved.getSenderName());
        assertEquals("I will arrive at 8:10 AM.", saved.getBody());
        assertEquals(ride, saved.getRide());
        assertTrue(ride.getMessages().contains(saved));
    }


    @Test
    void pollingReturnsOnlyMessagesAfterTheLastDisplayedId() {
        RideMessage newMessage = new RideMessage(
                ride, "driver@sfu.ca", "Demo Driver", "I am outside.");
        newMessage.setId(51L);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(messageRepository
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(10L, 50L))
                .thenReturn(List.of(newMessage));

        var messages = service.loadMessagesAfter(10L, driver, 50L);

        assertEquals(1, messages.size());
        assertEquals(51L, messages.getFirst().id());
        assertEquals("I am outside.", messages.getFirst().body());
        assertTrue(messages.getFirst().mine());
        verifyNoInteractions(requestRepository);
    }

    @Test
    void pollingAfterAlreadyDisplayedMessageReturnsNoDuplicate() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(messageRepository
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(10L, 51L))
                .thenReturn(List.of());

        var messages = service.loadMessagesAfter(10L, driver, 51L);

        assertTrue(messages.isEmpty());
        verify(messageRepository)
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(10L, 51L);
        verifyNoInteractions(requestRepository);
    }

    @Test
    void pendingRiderCannotPollMessages() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(false);

        RideChatAccessException error = assertThrows(RideChatAccessException.class,
                () -> service.loadMessagesAfter(10L, rider, 50L));

        assertEquals("Only the ride owner and confirmed riders can access this chat.",
                error.getMessage());
        verify(messageRepository, never())
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(any(), any());
    }

    @Test
    void blankMessageIsRejectedWithoutSaving() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        RideChatValidationException error = assertThrows(RideChatValidationException.class,
                () -> service.sendMessage(10L, driver, "   \n  "));

        assertEquals("Message cannot be empty.", error.getMessage());
        verify(messageRepository, never()).save(any(RideMessage.class));
    }

    @Test
    void overlongMessageIsRejectedWithoutSaving() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        String body = "x".repeat(RideChatService.MAX_MESSAGE_LENGTH + 1);

        RideChatValidationException error = assertThrows(RideChatValidationException.class,
                () -> service.sendMessage(10L, driver, body));

        assertEquals("Message cannot be longer than 1000 characters.", error.getMessage());
        verify(messageRepository, never()).save(any(RideMessage.class));
    }

    @Test
    void riderWhoseRequestIsNoLongerConfirmedCannotSendMessage() {
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(false);

        RideChatAccessException error = assertThrows(RideChatAccessException.class,
                () -> service.sendMessage(10L, rider, "I am outside."));

        assertEquals("Only the ride owner and confirmed riders can access this chat.",
                error.getMessage());
        assertTrue(ride.getMessages().isEmpty());
        verify(messageRepository, never()).save(any(RideMessage.class));
    }

    @Test
    void confirmedRiderMessageIsMarkedAsMineWhenChatReloads() {
        RideMessage message = new RideMessage(
                ride, "rider@sfu.ca", "Demo Rider", "On my way.");
        message.setId(50L);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(requestRepository.existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                10L, "rider@sfu.ca", CHAT_ACCESS_STATUSES)).thenReturn(true);
        when(messageRepository.findTop100ByRide_IdOrderByIdDesc(10L))
                .thenReturn(List.of(message));

        RideChatView chat = service.openChat(10L, rider);

        assertTrue(chat.messages().getFirst().mine());
        ArgumentCaptor<Long> rideId = ArgumentCaptor.forClass(Long.class);
        verify(messageRepository).findTop100ByRide_IdOrderByIdDesc(rideId.capture());
        assertEquals(10L, rideId.getValue());
    }

    @Test
    void driverSeesRiderNameAndEmailWithoutMineLabel() {
        RideMessage message = new RideMessage(
                ride, "rider@sfu.ca", "Demo Rider", "I am at the pickup point.");
        message.setId(60L);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(messageRepository.findTop100ByRide_IdOrderByIdDesc(10L))
                .thenReturn(List.of(message));

        RideChatView chat = service.openChat(10L, driver);

        assertEquals("Demo Rider", chat.messages().getFirst().senderName());
        assertEquals("rider@sfu.ca", chat.messages().getFirst().senderEmail());
        assertFalse(chat.messages().getFirst().mine());
        verifyNoInteractions(requestRepository);
    }
}
