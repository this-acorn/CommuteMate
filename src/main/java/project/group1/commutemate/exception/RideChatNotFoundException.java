package project.group1.commutemate.exception;

/** The requested ride conversation does not exist. */
public class RideChatNotFoundException extends RideOperationException {

    public RideChatNotFoundException(String message) {
        super(message);
    }
}
