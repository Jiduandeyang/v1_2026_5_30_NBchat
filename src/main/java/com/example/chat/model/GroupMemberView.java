package com.example.chat.model;

public record GroupMemberView(
        long userId,
        String username,
        String nickname,
        String avatarUrl,
        String role
) {
}
