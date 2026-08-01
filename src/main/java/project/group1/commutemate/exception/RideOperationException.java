package project.group1.commutemate.exception;

import java.util.Objects;

/** A business-rule failure safe to display as an error banner. */
public class RideOperationException extends RuntimeException {

    public enum ErrorCode {
        GENERAL,
        RIDE_NOT_FOUND,
        DRIVER_REQUIRED,
        LOCATION_REQUIRED,
        SAME_ROUTE,
        DEPARTURE_INVALID,
        SEATS_INVALID,
        PRICE_INVALID
    }

    private final ErrorCode errorCode;

    public RideOperationException(String message) {
        this(ErrorCode.GENERAL, message);
    }

    public RideOperationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
