package com.alves_dev.sos.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class FilenameNormalizer {

    private static final int MAX_LENGTH = 255;

    public String derive(String originalFilename) {
        String candidate = StringUtils.hasText(originalFilename) ? originalFilename : "file";
        candidate = candidate.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (candidate.equals(".") || candidate.equals("..") || candidate.isBlank()) {
            candidate = "file";
        }
        if (candidate.length() > MAX_LENGTH) {
            candidate = candidate.substring(0, MAX_LENGTH);
        }
        return candidate;
    }

    public String normalize(String filename) {
        return filename.toLowerCase(Locale.ROOT);
    }

    public String disambiguate(String filename, String fileId) {
        String prefix = fileId == null ? "legacy" : fileId.substring(0, Math.min(8, fileId.length()));
        int available = Math.max(1, MAX_LENGTH - prefix.length() - 1);
        String shortened = filename.length() > available ? filename.substring(0, available) : filename;
        return prefix + "_" + shortened;
    }
}
