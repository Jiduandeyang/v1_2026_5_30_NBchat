package com.example.chat.media;

import java.util.Locale;

public final class MediaUrlBuilder {
    private MediaUrlBuilder() {
    }

    private static final String UPLOADS_PATH = "/uploads/";

    public static String build(String publicBaseUrl, UploadKind kind, String storedName) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = UPLOADS_PATH + kind.name().toLowerCase(Locale.ROOT) + "/" + storedName;
        return base.isBlank() ? path : base + path;
    }

    public static String normalize(String publicBaseUrl, String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        int uploadsIndex = storedUrl.indexOf(UPLOADS_PATH);
        if (uploadsIndex >= 0 && !base.isBlank()) {
            return base + storedUrl.substring(uploadsIndex);
        }
        if (storedUrl.startsWith("http://") || storedUrl.startsWith("https://")) {
            return storedUrl;
        }
        if (base.isBlank()) {
            return storedUrl;
        }
        return storedUrl.startsWith("/") ? base + storedUrl : base + "/" + storedUrl;
    }
}
