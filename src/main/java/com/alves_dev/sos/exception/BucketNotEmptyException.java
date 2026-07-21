package com.alves_dev.sos.exception;

public class BucketNotEmptyException extends RuntimeException {
    public BucketNotEmptyException(String bucketName) {
        super("Bucket '" + bucketName + "' is not empty");
    }
}
