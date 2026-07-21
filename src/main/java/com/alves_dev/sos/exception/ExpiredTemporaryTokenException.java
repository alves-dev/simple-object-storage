package com.alves_dev.sos.exception;

public class ExpiredTemporaryTokenException extends RuntimeException {
    public ExpiredTemporaryTokenException() {
        super("Temporary token has expired");
    }
}
