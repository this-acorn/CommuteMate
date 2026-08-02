package project.group1.commutemate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Tracks how far one ride participant has read in one ride conversation. */
@Entity
@Table(name = "ride_chat_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_read_ride_reader",
                columnNames = {"ride_id", "reader_email"}),
        indexes = @Index(
                name = "idx_chat_read_reader",
                columnList = "reader_email"))
public class RideChatReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @Column(name = "reader_email", nullable = false, length = 190)
    private String readerEmail;

    @Column(name = "last_read_message_id", nullable = false)
    private long lastReadMessageId;

    protected RideChatReadState() {
        // JPA
    }

    public RideChatReadState(Ride ride, String readerEmail, long lastReadMessageId) {
        this.ride = ride;
        this.readerEmail = readerEmail;
        this.lastReadMessageId = lastReadMessageId;
    }

    public Long getId() {
        return id;
    }

    public Ride getRide() {
        return ride;
    }

    public String getReaderEmail() {
        return readerEmail;
    }

    public long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void markReadThrough(long messageId) {
        if (messageId > lastReadMessageId) {
            lastReadMessageId = messageId;
        }
    }
}
