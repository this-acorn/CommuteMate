package project.group1.commutemate.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** A persistent carpool ride offered by a verified CommuteMate driver. */
@Entity
@Table(name = "rides")
public class Ride {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_email", nullable = false, length = 190)
    private String driverEmail;

    @Column(name = "driver_name", nullable = false, length = 120)
    private String driver;

    @Column(name = "driver_initials", nullable = false, length = 4)
    private String driverInitials;

    @Column(name = "pickup_location", nullable = false, length = 180)
    private String from;

    @Column(name = "destination", nullable = false, length = 180)
    private String to;

    @Column(name = "depart_at", nullable = false)
    private LocalDateTime departAt;

    @Column(nullable = false)
    private int seats;

    @Column(name = "seats_taken", nullable = false)
    private int seatsTaken;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int points;

    @Column(name = "eco_score", nullable = false)
    private int ecoScore;

    @Column(nullable = false, length = 120)
    private String car;

    @Column(nullable = false)
    private double rating;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "ride", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RideRequest> requests = new ArrayList<>();

    @OneToMany(mappedBy = "ride", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RideMessage> messages = new ArrayList<>();

    public Ride() {
    }

    public Ride(String driverEmail, String driver, String driverInitials, String from, String to,
                LocalDateTime departAt, int seats, int seatsTaken, int price, int points,
                int ecoScore, String car, double rating, String notes) {
        this.driverEmail = driverEmail;
        this.driver = driver;
        this.driverInitials = driverInitials;
        this.from = from;
        this.to = to;
        this.departAt = departAt;
        setSeats(seats);
        setSeatsTaken(seatsTaken);
        this.price = price;
        this.points = points;
        this.ecoScore = ecoScore;
        this.car = car;
        this.rating = rating;
        this.notes = notes;
    }

    public int getSeatsLeft() {
        return seats - seatsTaken;
    }

    public boolean isFull() {
        return seatsTaken >= seats;
    }

    // Reserves exactly one seat after request is confirmed
    public void reserveSeat() {
        if (isFull()) {
            throw new IllegalStateException("Cannot reserve a seat on a full ride.");
        }
        seatsTaken++;
    }

    // Releases exactly one seat after a confirmed request is cancelled
    public void releaseSeat() {
        if (seatsTaken <= 0) {
            throw new IllegalStateException("Cannot release a seat when none are reserved.");
        }
        seatsTaken--;
    }

    public String getDepartTime() {
        return departAt == null ? "" : departAt.format(TIME_FMT);
    }

    public String getDepartDate() {
        return departAt == null ? "" : departAt.format(DATE_FMT);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDriverEmail() {
        return driverEmail;
    }

    public void setDriverEmail(String driverEmail) {
        this.driverEmail = driverEmail;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getDriverInitials() {
        return driverInitials;
    }

    public void setDriverInitials(String driverInitials) {
        this.driverInitials = driverInitials;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public LocalDateTime getDepartAt() {
        return departAt;
    }

    public void setDepartAt(LocalDateTime departAt) {
        this.departAt = departAt;
    }

    public int getSeats() {
        return seats;
    }

    // Seats setter ensures that seatsTaken does not exceed seats
    public void setSeats(int seats) {
        if (seats < 1) {
            throw new IllegalArgumentException("A ride must have at least one seat.");
        }
        if (seatsTaken > seats) {
            throw new IllegalArgumentException("Reserved seats cannot exceed total seats.");
        }
        this.seats = seats;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    // SeatsTaken setter ensures that seatsTaken does not exceed seats
    public void setSeatsTaken(int seatsTaken) {
        if (seatsTaken < 0) {
            throw new IllegalArgumentException("Reserved seats cannot be negative.");
        }
        if (seats > 0 && seatsTaken > seats) {
            throw new IllegalArgumentException("Reserved seats cannot exceed total seats.");
        }
        this.seatsTaken = seatsTaken;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getEcoScore() {
        return ecoScore;
    }

    public void setEcoScore(int ecoScore) {
        this.ecoScore = ecoScore;
    }

    public String getCar() {
        return car;
    }

    public void setCar(String car) {
        this.car = car;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<RideRequest> getRequests() {
        return requests;
    }

    public void addMessage(RideMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null.");
        }
        messages.add(message);
        message.setRide(this);
    }

    public List<RideMessage> getMessages() {
        return messages;
    }
}
