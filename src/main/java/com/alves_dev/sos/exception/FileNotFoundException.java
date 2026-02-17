package com.alves_dev.sos.exception;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String fileId) {
        super("File with ID '" + fileId + "' does not exist");
    }
}
