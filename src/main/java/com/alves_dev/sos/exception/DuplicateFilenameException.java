package com.alves_dev.sos.exception;

public class DuplicateFilenameException extends RuntimeException {
    public DuplicateFilenameException(String filename) {
        super("Filename '" + filename + "' already exists in this bucket");
    }
}
