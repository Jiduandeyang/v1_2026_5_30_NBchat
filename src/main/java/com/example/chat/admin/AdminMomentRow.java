package com.example.chat.admin;

import java.time.LocalDateTime;

public record AdminMomentRow(
        long id,
        long authorId,
        String authorName,
        String text,
        String visibility,
        boolean deleted,
        int mediaCount,
        int likeCount,
        int commentCount,
        LocalDateTime createdAt
) {
}
