package project.group1.commutemate.model;

/** Display-safe chat message data prepared for the Thymeleaf view. */
public record RideMessageView(
        Long id,
        String senderEmail,
        String senderName,
        String body,
        String sentAt,
        boolean mine) {
}
