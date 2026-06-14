package com.example.chat.model;

public record User(
        long id,
        String username,
        String qqEmail,
        String nickname,
        String avatarUrl,
        String backgroundUrl,
        String signature,
        String role,
        boolean disabled,
        Long groupId,
        boolean closeFriend
) {
}
