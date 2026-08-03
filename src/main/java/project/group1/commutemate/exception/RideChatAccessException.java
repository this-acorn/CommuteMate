package project.group1.commutemate.exception;

/** The signed-in member is not allowed to access a ride conversation. */
public class RideChatAccessException extends RideOperationException {

    public RideChatAccessException(String message) {
        super(message);
    }
}
