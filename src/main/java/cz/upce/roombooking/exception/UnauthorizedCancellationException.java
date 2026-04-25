package cz.upce.roombooking.exception;

public class UnauthorizedCancellationException extends RuntimeException {

    public UnauthorizedCancellationException(String message) {
        super(message);
    }
}
