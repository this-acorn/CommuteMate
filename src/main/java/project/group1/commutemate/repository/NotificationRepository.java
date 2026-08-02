package project.group1.commutemate.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import project.group1.commutemate.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Newest first, for the "all notifications" page
    List<Notification> findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(String recipientEmail);

    // Used for the bell-icon badge count on every page (see AuthenticatedController)
    long countByRecipientEmailIgnoreCaseAndReadFalse(String recipientEmail);
}