package com.alves_dev.sos.exception;

public class BucketDisabledException extends RuntimeException {
    public BucketDisabledException(String bucketName) {
        super("Bucket '" + bucketName + "' is disabled");
    }
}
