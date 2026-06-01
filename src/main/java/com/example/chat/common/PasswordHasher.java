package com.example.chat.common;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {
    private PasswordHasher() {
    }

    public static String hash(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt(12));
    }

    public static boolean verify(String plainText, String hash) {
        if (plainText == null || hash == null || hash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(plainText, hash);
    }
}
