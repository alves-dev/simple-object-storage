package com.alves_dev.sos.exception;

public class ContentNotFoundException extends RuntimeException {
    public ContentNotFoundException(String fileId) {
        super("Content for file '" + fileId + "' is not available");
    }
}
