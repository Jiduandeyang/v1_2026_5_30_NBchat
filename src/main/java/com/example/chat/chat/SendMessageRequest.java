package com.example.chat.chat;

public record SendMessageRequest(long conversationId, String type, String content, Long mediaId, Long replyToMessageId) {
}
