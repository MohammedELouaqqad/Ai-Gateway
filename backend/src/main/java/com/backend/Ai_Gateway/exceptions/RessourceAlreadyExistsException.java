package com.backend.Ai_Gateway.exceptions;

public class RessourceAlreadyExistsException extends RuntimeException {
    public RessourceAlreadyExistsException(String message) {
        super(message);
    }
}
