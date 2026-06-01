package com.example.chat.auth;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailCodeCooldownTest {
    @Test
    void calculatesRemainingCooldownSeconds() {
        LocalDateTime now = LocalDateTime.parse("2026-06-01T18:30:00");

        assertEquals(180, EmailCodeCooldown.remainingSeconds(now, now));
        assertEquals(60, EmailCodeCooldown.remainingSeconds(now.minusMinutes(2), now));
        assertEquals(1, EmailCodeCooldown.remainingSeconds(now.minusSeconds(179), now));
        assertEquals(0, EmailCodeCooldown.remainingSeconds(now.minusMinutes(3), now));
        assertEquals(0, EmailCodeCooldown.remainingSeconds(null, now));
    }
}
