package com.example.chat.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaContentTypePolicyTest {
    @Test
    void imageKindsAllowOnlySafeImageTypes() {
        assertTrue(MediaContentTypePolicy.allows(UploadKind.CHAT_IMAGE, "image/png"));
        assertTrue(MediaContentTypePolicy.allows(UploadKind.MOMENT_IMAGE, "image/jpeg"));
        assertTrue(MediaContentTypePolicy.allows(UploadKind.AVATAR, "image/webp"));

        assertFalse(MediaContentTypePolicy.allows(UploadKind.CHAT_IMAGE, "text/html"));
        assertFalse(MediaContentTypePolicy.allows(UploadKind.MOMENT_IMAGE, "image/svg+xml"));
        assertFalse(MediaContentTypePolicy.allows(UploadKind.BACKGROUND, null));
    }

    @Test
    void videoKindAllowsOnlyMp4AndWebm() {
        assertTrue(MediaContentTypePolicy.allows(UploadKind.MOMENT_VIDEO, "video/mp4"));
        assertTrue(MediaContentTypePolicy.allows(UploadKind.MOMENT_VIDEO, "video/webm"));

        assertFalse(MediaContentTypePolicy.allows(UploadKind.MOMENT_VIDEO, "application/octet-stream"));
    }

    @Test
    void voiceMessageKindAllowsOnlySafeAudioTypes() {
        assertTrue(MediaContentTypePolicy.allows(UploadKind.VOICE_MESSAGE, "audio/webm"));
        assertTrue(MediaContentTypePolicy.allows(UploadKind.VOICE_MESSAGE, "audio/ogg; codecs=opus"));

        assertFalse(MediaContentTypePolicy.allows(UploadKind.VOICE_MESSAGE, "text/plain"));
    }
}
