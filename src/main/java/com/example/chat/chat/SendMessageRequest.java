package com.example.chat.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record SendMessageRequest(long conversationId, String type, String content, Long mediaId, Long replyToMessageId, String unlockAt) {
    public SendMessageRequest(long conversationId, String type, String content, Long mediaId, Long replyToMessageId) {
        this(conversationId, type, content, mediaId, replyToMessageId, null);
    }

    @JsonIgnore
    public LocalDateTime unlockAtDateTime() {
        if (unlockAt == null || unlockAt.isBlank()) {
            return null;
        }
        String normalized = unlockAt.trim();
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(normalized);
        }
    }
}
