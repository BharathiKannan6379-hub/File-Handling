package com.sms;

/**
 * Thrown when student input or business validation fails.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
