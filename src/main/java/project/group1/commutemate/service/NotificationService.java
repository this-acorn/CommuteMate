package project.group1.commutemate.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.group1.commutemate.model.Notification;
import project.group1.commutemate.repository.NotificationRepository;

/**
 * Creates and reads in-app notifications. Deliberately separate
 * from RideCoordinationService — that class already handles the request
 * lifecycle's business rules, and mixing "send a notification" into it
 * would blur what each class is responsible for. Controllers call both:
 * the coordination service to make the change, then this service to
 * announce it (see RideRequestController).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(String recipientEmail, String message, Long relatedRideId) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return; // nothing to notify — fail quietly, this is a side effect, not core logic
        }
        notificationRepository.save(
                new Notification(recipientEmail.trim().toLowerCase(), message, relatedRideId));
    }

    public List<Notification> findForUser(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return notificationRepository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(email);
    }

    public long unreadCountFor(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        return notificationRepository.countByRecipientEmailIgnoreCaseAndReadFalse(email);
    }

    // Marks every one of this member's notifications as read — simpler than
    // per-notification read state for a v1, and matches how most students'
    // familiar apps behave when you open the notification list.
    @Transactional
    public void markAllRead(String email) {
        List<Notification> unread = findForUser(email).stream()
                .filter(n -> !n.isRead())
                .toList();
        for (Notification notification : unread) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    // Marks a single notification read (e.g. after "View ride" is clicked).
    // Ownership check mirrors the pattern used everywhere else in the app
    // (see RideCoordinationService's requireRideOwner-style checks) — the
    // notification ID is a client-supplied path variable, so we don't trust
    // it belongs to whoever is asking without checking first.
    @Transactional
    public Optional<Notification> markRead(Long notificationId, String ownerEmail) {
        if (notificationId == null || ownerEmail == null || ownerEmail.isBlank()) {
            return Optional.empty();
        }
        Optional<Notification> found = notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipientEmail().equalsIgnoreCase(ownerEmail));
        found.ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return found;
    }
}