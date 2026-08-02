package project.group1.commutemate.service;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.exception.RideChatAccessException;
import project.group1.commutemate.exception.RideChatNotFoundException;
import project.group1.commutemate.exception.RideChatValidationException;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.model.RequestStatus;
import project.group1.commutemate.model.Ride;
import project.group1.commutemate.model.RideChatReadState;
import project.group1.commutemate.model.RideChatView;
import project.group1.commutemate.model.RideMessage;
import project.group1.commutemate.model.RideMessageView;
import project.group1.commutemate.repository.RideChatReadStateRepository;
import project.group1.commutemate.repository.RideLatestMessageId;
import project.group1.commutemate.repository.RideMessageRepository;
import project.group1.commutemate.repository.RideRepository;
import project.group1.commutemate.repository.RideRequestRepository;

/** Persistent in-app chat for ride owners and confirmed riders. */
@Service
public class RideChatService {

    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MESSAGE_BATCH_SIZE = 100;

    private static final List<RequestStatus> CHAT_ACCESS_STATUSES = List.of(
            RequestStatus.CONFIRMED,
            RequestStatus.BOARDING_CONFIRMED,
            RequestStatus.COMPLETED);

    private static final DateTimeFormatter MESSAGE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    private final RideRepository rideRepository;
    private final RideRequestRepository requestRepository;
    private final RideMessageRepository messageRepository;
    private final RideChatReadStateRepository readStateRepository;
    private final Clock clock;

    public RideChatService(RideRepository rideRepository,
                           RideRequestRepository requestRepository,
                           RideMessageRepository messageRepository,
                           RideChatReadStateRepository readStateRepository,
                           Clock clock) {
        this.rideRepository = rideRepository;
        this.requestRepository = requestRepository;
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.clock = clock;
    }

    /** Loads the latest message batch after confirming that the member belongs to the ride. */
    @Transactional
    public RideChatView openChat(Long rideId, Profile member) {
        Ride ride = findRide(rideId);
        boolean owner = requireParticipant(ride, member);

        List<RideMessage> newestFirst = new ArrayList<>(
                messageRepository.findTop100ByRide_IdOrderByIdDesc(rideId));
        Collections.reverse(newestFirst);
        markReadThrough(ride, member, newestFirst);

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
        String email = normalizeEmail(sender.getEmail());
        String name = sender.getFullName() == null || sender.getFullName().isBlank()
                ? email
                : sender.getFullName().trim();

        RideMessage message = new RideMessage(ride, email, name, body);
        ride.addMessage(message);
        return messageRepository.save(message);
    }

    /** Returns one ID-ordered batch after the last message already displayed. */
    @Transactional
    public List<RideMessageView> loadMessagesAfter(
            Long rideId, Profile member, Long afterId) {
        Ride ride = findRide(rideId);
        requireParticipant(ride, member);

        long safeAfterId = afterId == null || afterId < 0 ? 0 : afterId;
        List<RideMessage> messages = messageRepository
                .findTop100ByRide_IdAndIdGreaterThanOrderByIdAsc(
                        rideId, safeAfterId);
        markReadThrough(ride, member, messages);
        return messages.stream()
                .map(message -> toView(message, member))
                .toList();
    }

    /** Returns the rides whose chat has at least one message this member has not read. */
    @Transactional(readOnly = true)
    public Set<Long> findUnreadRideIds(Profile member, Collection<Long> rideIds) {
        if (member == null || member.getEmail() == null || member.getEmail().isBlank()
                || rideIds == null || rideIds.isEmpty()) {
            return Set.of();
        }

        String readerEmail = normalizeEmail(member.getEmail());
        Set<Long> distinctRideIds = new LinkedHashSet<>();
        for (Long rideId : rideIds) {
            if (rideId != null) {
                distinctRideIds.add(rideId);
            }
        }
        if (distinctRideIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, Long> lastReadByRide = new HashMap<>();
        for (RideChatReadState state : readStateRepository
                .findByRide_IdInAndReaderEmailIgnoreCase(
                        distinctRideIds, readerEmail)) {
            lastReadByRide.put(
                    state.getRide().getId(), state.getLastReadMessageId());
        }

        Set<Long> unreadRideIds = new LinkedHashSet<>();
        for (RideLatestMessageId latest : messageRepository
                .findLatestOtherMessageIds(distinctRideIds, readerEmail)) {
            if (latest.rideId() != null && latest.latestMessageId() != null
                    && latest.latestMessageId()
                    > lastReadByRide.getOrDefault(latest.rideId(), 0L)) {
                unreadRideIds.add(latest.rideId());
            }
        }
        return Set.copyOf(unreadRideIds);
    }

    private void markReadThrough(Ride ride, Profile member, List<RideMessage> messages) {
        long newestMessageId = messages.stream()
                .map(RideMessage::getId)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        if (newestMessageId == 0L) {
            return;
        }

        String readerEmail = normalizeEmail(member.getEmail());
        RideChatReadState readState = readStateRepository
                .findByRide_IdAndReaderEmailIgnoreCase(ride.getId(), readerEmail)
                .orElseGet(() -> {
                    RideChatReadState created =
                            new RideChatReadState(ride, readerEmail, 0L);
                    ride.addChatReadState(created);
                    return created;
                });
        readState.markReadThrough(newestMessageId);
        readStateRepository.save(readState);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
                .existsByRide_IdAndRiderEmailIgnoreCaseAndStatusIn(
                        ride.getId(), email, CHAT_ACCESS_STATUSES);
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
