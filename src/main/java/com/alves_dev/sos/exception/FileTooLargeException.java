package com.alves_dev.sos.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("File exceeds the maximum allowed size");
    }
}
