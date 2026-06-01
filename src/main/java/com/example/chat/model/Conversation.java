package com.example.chat.model;

import java.time.LocalDateTime;

public record Conversation(
        long id,
        String type,
        String title,
        Long peerId,
        String peerName,
        String peerAvatarUrl,
        String lastMessage,
        String lastMessageType,
        LocalDateTime lastSentAt,
        int unreadCount
) {
}
