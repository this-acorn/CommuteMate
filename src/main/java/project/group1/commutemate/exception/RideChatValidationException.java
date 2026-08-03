package project.group1.commutemate.exception;

/** A chat message failed user-input validation. */
public class RideChatValidationException extends RideOperationException {

    public RideChatValidationException(String message) {
        super(message);
    }
}
