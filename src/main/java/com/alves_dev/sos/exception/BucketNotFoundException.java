package com.alves_dev.sos.exception;

public class BucketNotFoundException extends RuntimeException {
    public BucketNotFoundException(String bucketName) {
        super("Bucket '" + bucketName + "' does not exist");
    }
}
