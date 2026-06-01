package com.example.chat.common;

import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern QQ_EMAIL_PATTERN = Pattern.compile("^[1-9][0-9]{4,11}@qq\\.com$", Pattern.CASE_INSENSITIVE);

    private Validation() {
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw AppException.badRequest(message);
        }
    }

    public static void qqEmail(String email) {
        require(email != null && QQ_EMAIL_PATTERN.matcher(email.trim()).matches(), "Only numeric QQ email is supported.");
    }

    public static String normalizeQqEmail(String email) {
        qqEmail(email);
        return email.trim().toLowerCase();
    }

    public static void notBlank(String value, String message) {
        require(value != null && !value.isBlank(), message);
    }
}
