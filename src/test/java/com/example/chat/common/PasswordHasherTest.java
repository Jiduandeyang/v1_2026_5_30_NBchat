package com.example.chat.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void hashDoesNotExposePlainTextAndCanVerify() {
        String hash = PasswordHasher.hash("Secret123!");

        assertNotEquals("Secret123!", hash);
        assertTrue(PasswordHasher.verify("Secret123!", hash));
        assertFalse(PasswordHasher.verify("wrong", hash));
    }
}
