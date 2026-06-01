package com.example.chat.model;

public record RecallUpdate(String event, long conversationId, ChatMessage message) {
    public static RecallUpdate of(ChatMessage message) {
        return new RecallUpdate("RECALL", message.conversationId(), message);
    }
}
