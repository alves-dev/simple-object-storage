package com.alves_dev.sos.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class FileNameGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_ID_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a unique stored file name in the format:
     * {yyyyMMddHHmmss}_{randomId}_{originalFilename}
     */
    public String generate(String filename) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String randomId = randomAlphanumeric(RANDOM_ID_LENGTH);
        return timestamp + "_" + randomId + "_" + filename;
    }

    private String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}