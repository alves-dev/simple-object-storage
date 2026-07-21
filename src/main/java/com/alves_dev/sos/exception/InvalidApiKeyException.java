package com.alves_dev.sos.exception;

public class InvalidApiKeyException extends RuntimeException {
    public InvalidApiKeyException(String message) {
        super(message);
    }
}
