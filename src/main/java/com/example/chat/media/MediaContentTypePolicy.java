package com.example.chat.media;

import java.util.Locale;
import java.util.Set;

public final class MediaContentTypePolicy {
    private static final Set<String> SAFE_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> SAFE_VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Set<String> SAFE_AUDIO_TYPES = Set.of("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav");

    private MediaContentTypePolicy() {
    }

    public static boolean allows(UploadKind kind, String contentType) {
        if (kind == null || contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case CHAT_IMAGE, MOMENT_IMAGE, AVATAR, BACKGROUND -> SAFE_IMAGE_TYPES.contains(normalized);
            case MOMENT_VIDEO -> SAFE_VIDEO_TYPES.contains(normalized);
            case VOICE_MESSAGE -> SAFE_AUDIO_TYPES.contains(normalized);
        };
    }
}
