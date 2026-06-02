package com.example.chat.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaUrlBuilderTest {
    @Test
    void buildsUrlUnderConfiguredPublicBaseUrl() {
        String url = MediaUrlBuilder.build("http://localhost:8080/v1_2026_5_30", UploadKind.MOMENT_IMAGE, "a.png");

        assertEquals("http://localhost:8080/v1_2026_5_30/uploads/moment_image/a.png", url);
    }

    @Test
    void trimsTrailingSlashFromPublicBaseUrl() {
        String url = MediaUrlBuilder.build("http://localhost:8080/v1_2026_5_30/", UploadKind.CHAT_IMAGE, "b.png");

        assertEquals("http://localhost:8080/v1_2026_5_30/uploads/chat_image/b.png", url);
    }

    @Test
    void normalizesExistingRootRelativeUploadUrl() {
        String url = MediaUrlBuilder.normalize("http://localhost:8080/v1_2026_5_30", "/uploads/moment_image/old.png");

        assertEquals("http://localhost:8080/v1_2026_5_30/uploads/moment_image/old.png", url);
    }

    @Test
    void keepsExistingAbsoluteUrl() {
        String url = MediaUrlBuilder.normalize("http://localhost:8080/v1_2026_5_30", "https://cdn.example.com/a.png");

        assertEquals("https://cdn.example.com/a.png", url);
    }

    @Test
    void rewritesOldAbsoluteUploadUrlToConfiguredPublicBaseUrl() {
        String url = MediaUrlBuilder.normalize(
                "https://nbchatroom.cloud/v1_2026_5_30",
                "http://175.178.56.39/v1_2026_5_30/uploads/moment_image/old.png"
        );

        assertEquals("https://nbchatroom.cloud/v1_2026_5_30/uploads/moment_image/old.png", url);
    }

    @Test
    void rewritesMalformedEnvironmentPrefixedUploadUrl() {
        String url = MediaUrlBuilder.normalize(
                "https://nbchatroom.cloud/v1_2026_5_30",
                "Environment=PUBLIC_BASEURL=https://nbchatroom.cloud/v1_2026_5_30/uploads/voice_message/old.webm"
        );

        assertEquals("https://nbchatroom.cloud/v1_2026_5_30/uploads/voice_message/old.webm", url);
    }
}
