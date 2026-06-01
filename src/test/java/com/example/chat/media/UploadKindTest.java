package com.example.chat.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadKindTest {
    @Test
    void enforcesConfiguredUploadLimits() {
        assertTrue(UploadKind.CHAT_IMAGE.allowsBytes(5L * 1024 * 1024));
        assertFalse(UploadKind.CHAT_IMAGE.allowsBytes(5L * 1024 * 1024 + 1));
        assertTrue(UploadKind.MOMENT_VIDEO.allowsBytes(50L * 1024 * 1024));
        assertFalse(UploadKind.AVATAR.allowsBytes(2L * 1024 * 1024 + 1));
    }
}
