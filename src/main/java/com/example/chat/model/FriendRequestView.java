package com.example.chat.model;

import java.time.LocalDateTime;

public record FriendRequestView(
        long id,
        long senderId,
        long receiverId,
        String senderName,
        String receiverName,
        String message,
        String status,
        LocalDateTime createdAt
) {
}
