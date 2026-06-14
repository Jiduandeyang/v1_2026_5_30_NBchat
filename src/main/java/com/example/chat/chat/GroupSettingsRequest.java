package com.example.chat.chat;

public record GroupSettingsRequest(
        String remark,
        Boolean muted,
        String backgroundKey,
        String backgroundUrl
) {
}
