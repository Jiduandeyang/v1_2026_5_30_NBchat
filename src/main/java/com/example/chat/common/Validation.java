package com.example.chat.common;

public final class Validation {
    private Validation() {
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw AppException.badRequest(message);
        }
    }

    public static void qqEmail(String email) {
        require(email != null && email.toLowerCase().endsWith("@qq.com"), "Only QQ email is supported.");
    }

    public static void notBlank(String value, String message) {
        require(value != null && !value.isBlank(), message);
    }
}
