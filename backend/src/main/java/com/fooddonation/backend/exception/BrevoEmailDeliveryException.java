package com.fooddonation.backend.exception;

public class BrevoEmailDeliveryException extends RuntimeException {

    public BrevoEmailDeliveryException() {
        super("Email could not be delivered via Brevo");
    }

    public BrevoEmailDeliveryException(String message) {
        super(message);
    }

    public BrevoEmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
