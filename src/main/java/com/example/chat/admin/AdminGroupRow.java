package com.example.chat.admin;

import java.time.LocalDateTime;

public record AdminGroupRow(
        long groupId,
        long conversationId,
        String name,
        String ownerName,
        int memberCount,
        int messageCount,
        LocalDateTime createdAt
) {
}
