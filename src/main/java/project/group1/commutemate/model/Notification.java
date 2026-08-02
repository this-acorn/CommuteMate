package project.group1.commutemate.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single in-app notification for one member, about one ride request lifecycle event 
 * (new request/confirmed/rejected/cancelled/boarding confirmed/ride completed)
 * Kept deliberately simple — a flat message string rather than a templated/typed system, 
 * since the UI only ever needs to display it as-is.
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who this notification is for — matches Profile.email, same convention
    // as RideRequest.riderEmail (stored lowercase, compared case-insensitively).
    @Column(name = "recipient_email", nullable = false, length = 190)
    private String recipientEmail;

    @Column(nullable = false, length = 300)
    private String message;

    // ** optional — lets the UI link "View ride" next to the notification.
    // Nullable because not every notification is tied to one ride (keeps
    // this entity reusable beyond epic5 if needed later)
    @Column(name = "related_ride_id")
    private Long relatedRideId;

    @Column(nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(String recipientEmail, String message, Long relatedRideId) {
        this.recipientEmail = recipientEmail;
        this.message = message;
        this.relatedRideId = relatedRideId;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getMessage() {
        return message;
    }

    public Long getRelatedRideId() {
        return relatedRideId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}