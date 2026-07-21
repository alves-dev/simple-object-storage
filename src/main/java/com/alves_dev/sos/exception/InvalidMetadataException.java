package com.alves_dev.sos.exception;

public class InvalidMetadataException extends RuntimeException {
    public InvalidMetadataException() {
        super("Metadata must be a valid JSON object");
    }
}
