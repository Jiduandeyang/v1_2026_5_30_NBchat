package com.example.chat.model;

import java.time.LocalDateTime;

public record VoiceCallSession(
        long id,
        long callerId,
        long calleeId,
        String callMode,
        String status,
        LocalDateTime startedAt,
        LocalDateTime acceptedAt,
        LocalDateTime endedAt
) {
}
