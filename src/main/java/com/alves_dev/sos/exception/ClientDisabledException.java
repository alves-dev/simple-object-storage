package com.alves_dev.sos.exception;

public class ClientDisabledException extends RuntimeException {
    public ClientDisabledException(String message) {
        super(message);
    }
}
