package com.claimchain.backend.security;

import org.springframework.stereotype.Component;

@Component
public class FilenameSanitizer {

    private static final int MAX_LENGTH = 120;
    private static final String FALLBACK_FILENAME = "document";

    public String sanitize(String originalFilename) {
        if (originalFilename == null) {
            return FALLBACK_FILENAME;
        }

        String value = originalFilename.replace('\\', '/');
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < value.length() - 1) {
            value = value.substring(lastSlash + 1);
        } else if (lastSlash == value.length() - 1) {
            value = "";
        }

        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }

        String sanitized = cleaned.toString().trim();
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH);
        }

        if (sanitized.isBlank()) {
            return FALLBACK_FILENAME;
        }

        return sanitized;
    }
}
