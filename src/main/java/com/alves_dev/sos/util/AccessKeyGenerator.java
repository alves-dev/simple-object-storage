package com.alves_dev.sos.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccessKeyGenerator {

    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ACCESS_KEY_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically secure 16-character alphanumeric access key
     * for private file access.
     */
    public String generate() {
        StringBuilder sb = new StringBuilder(ACCESS_KEY_LENGTH);
        for (int i = 0; i < ACCESS_KEY_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}