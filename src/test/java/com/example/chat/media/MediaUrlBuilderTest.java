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
}
