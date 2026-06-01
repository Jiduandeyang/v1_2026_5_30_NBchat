package com.example.chat.model;

import java.time.LocalDateTime;

public record ChatMessage(
        long id,
        long conversationId,
        long senderId,
        String senderName,
        String type,
        String content,
        Long mediaId,
        String mediaUrl,
        Long replyToMessageId,
        String replySenderName,
        String replyPreview,
        java.util.List<MessageReactionSummary> reactions,
        LocalDateTime recalledAt,
        LocalDateTime sentAt
) {
}
