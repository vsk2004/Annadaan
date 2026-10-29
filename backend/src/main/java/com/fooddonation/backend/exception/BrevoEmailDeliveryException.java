package com.fooddonation.backend.exception;

public class BrevoEmailDeliveryException extends RuntimeException {
    public BrevoEmailDeliveryException() {
        super("Password reset email could not be delivered");
    }
}