package com.alves_dev.sos.exception;

public class VersionConflictException extends RuntimeException {
    public VersionConflictException(String fileId) {
        super("File '" + fileId + "' was modified concurrently");
    }
}
