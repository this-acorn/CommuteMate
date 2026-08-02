package project.group1.commutemate.repository;

/** Latest message from another participant for one ride chat. */
public record RideLatestMessageId(Long rideId, Long latestMessageId) {
}
