package project.group1.commutemate.service;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.exception.RideChatValidationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideMessage;
import project.group1.commutemate.model.RideMessageView;
import project.group1.commutemate.repository.RideMessageRepository;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

/** Persistent in-app chat for ride owners and confirmed riders. */
@Service
public class RideChatService {

    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MESSAGE_BATCH_SIZE = 100;

    private static final DateTimeFormatter MESSAGE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    private final RideRepository rideRepository;
    private final RideRequestRepository requestRepository;
    private final RideMessageRepository messageRepository;
    private final Clock clock;

    public RideChatService(RideRepository rideRepository,
                           RideRequestRepository requestRepository,
                           RideMessageRepository messageRepository,
                           Clock clock) {
        this.rideRepository = rideRepository;
        this.requestRepository = requestRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    /** Loads the latest message batch after confirming that the member belongs to the ride. */
    @Transactional(readOnly = true)
    public RideChatView openChat(Long rideId, Profile member) {
        Ride ride = findRide(rideId);
        boolean owner = requireParticipant(ride, member);

        List<RideMessage> newestFirst = new ArrayList<>(
                messageRepository.findTop100ByRide_IdOrderByIdDesc(rideId));
        Collections.reverse(newestFirst);

        List<RideMessageView> messages = newestFirst.stream()
                .map(message -> toView(message, member))
                .toList();
        return new RideChatView(ride, messages, owner);
    }

    /** Saves one message using the signed-in member's identity. */
    @Transactional
    public RideMessage sendMessage(Long rideId, Profile sender, String rawBody) {
        Ride ride = findRide(rideId);
        requireParticipant(ride, sender);

        String body = normalizeBody(rawBody);
        String email = sender.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = sender.getFullName() == null || sender.getFullName().isBlank()
                ? email
                : sender.getFullName().trim();

        RideMessage message = new RideMessage(ride, email, name, body);
        ride.addMessage(message);
        return messageRepository.save(message);
    }

    /** Returns one ID-ordered batch after the last message already displayed. */
    @Transactional(readOnly = true)
    public List<RideMessageView> loadMessagesAfter(
            Long rideId, Profile member, Long afterId) {
        Ride ride = findRide(rideId);
        requireParticipant(ride, member);

        long safeAfterId = afterId == null || afterId < 0 ? 0 : afterId;
        return messageRepository
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(
                        rideId, safeAfterId)
                .stream()
                .map(message -> toView(message, member))
                .toList();
    }

    private Ride findRide(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new RideChatNotFoundException("Ride not found."));
    }

    /**
     * @return true when the member is the ride owner, false when they are a confirmed rider
     */
    private boolean requireParticipant(Ride ride, Profile member) {
        if (member == null || member.getEmail() == null || member.getEmail().isBlank()) {
            throw new RideChatAccessException(
                    "A signed-in ride participant is required.");
        }

        String email = member.getEmail().trim();
        if (ride.getDriverEmail().equalsIgnoreCase(email)) {
            return true;
        }

        boolean confirmedRider = requestRepository
                .existsByRide_IdAndRiderEmailIgnoreCaseAndStatus(
                        ride.getId(), email, RequestStatus.CONFIRMED);
        if (!confirmedRider) {
            throw new RideChatAccessException(
                    "Only the ride owner and confirmed riders can access this chat.");
        }
        return false;
    }

    private String normalizeBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new RideChatValidationException("Message cannot be empty.");
        }
        String body = rawBody.trim();
        if (body.length() > MAX_MESSAGE_LENGTH) {
            throw new RideChatValidationException(
                    "Message cannot be longer than " + MAX_MESSAGE_LENGTH + " characters.");
        }
        return body;
    }

    private RideMessageView toView(RideMessage message, Profile member) {
        String sentAt = message.getCreatedAt() == null
                ? ""
                : message.getCreatedAt().atZone(clock.getZone()).format(MESSAGE_TIME_FORMAT);
        boolean mine = message.getSenderEmail().equalsIgnoreCase(member.getEmail());
        return new RideMessageView(
                message.getId(),
                message.getSenderEmail(),
                message.getSenderName(),
                message.getBody(),
                sentAt,
                mine);
    }
}
