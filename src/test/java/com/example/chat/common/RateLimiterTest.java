package com.example.chat.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void deniesRequestsAfterLimitWithinWindow() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
    }

    @Test
    void tracksKeysIndependently() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("bob"));
    }
}
