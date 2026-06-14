package com.example.chat.admin;

import java.time.LocalDateTime;

public record AdminUserRow(
        long id,
        String username,
        String qqEmail,
        String nickname,
        String role,
        boolean disabled,
        LocalDateTime createdAt
) {
}
