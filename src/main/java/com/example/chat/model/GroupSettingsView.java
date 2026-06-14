package com.example.chat.model;

public record GroupSettingsView(
        long conversationId,
        String remark,
        boolean muted,
        String backgroundKey,
        String backgroundUrl
) {
}
