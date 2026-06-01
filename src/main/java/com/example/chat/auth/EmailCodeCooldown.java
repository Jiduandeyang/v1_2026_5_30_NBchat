package com.example.chat.auth;

import java.time.Duration;
import java.time.LocalDateTime;

final class EmailCodeCooldown {
    static final long COOLDOWN_SECONDS = 180;

    private EmailCodeCooldown() {
    }

    static long remainingSeconds(LocalDateTime latestCreatedAt, LocalDateTime now) {
        if (latestCreatedAt == null || now == null) {
            return 0;
        }
        long elapsed = Duration.between(latestCreatedAt, now).getSeconds();
        return Math.max(0, COOLDOWN_SECONDS - elapsed);
    }
}
