package com.example.chat.chat;

public record SendMessageRequest(long conversationId, String type, String content, Long mediaId, Long replyToMessageId, String unlockAt) {
    public SendMessageRequest(long conversationId, String type, String content, Long mediaId, Long replyToMessageId) {
        this(conversationId, type, content, mediaId, replyToMessageId, null);
    }
}
