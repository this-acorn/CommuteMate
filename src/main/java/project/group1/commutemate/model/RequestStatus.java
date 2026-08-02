package project.group1.commutemate.model;

/** Rider's one-seat request for a ride. */
public enum RequestStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    BOARDING_CONFIRMED, 
    COMPLETED;

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}