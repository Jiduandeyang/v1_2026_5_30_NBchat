package com.example.chat.model;

import java.time.LocalDateTime;
import java.util.List;

public record MomentView(
        long id,
        long authorId,
        String authorName,
        String text,
        String visibility,
        LocalDateTime createdAt,
        List<MediaFile> media,
        int likeCount,
        int commentCount,
        boolean likedByMe
) {
}
