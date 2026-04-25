package cz.upce.roombooking.exception;

public class BookingValidationException extends RuntimeException {

    public BookingValidationException(String message) {
        super(message);
    }
}
