package com.example.chat.model;

import java.time.LocalDateTime;

public record MomentCommentView(
        long id,
        long momentId,
        long userId,
        String userName,
        String content,
        LocalDateTime createdAt
) {
}
