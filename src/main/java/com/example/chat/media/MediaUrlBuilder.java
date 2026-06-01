package com.example.chat.media;

import java.util.Locale;

public final class MediaUrlBuilder {
    private MediaUrlBuilder() {
    }

    public static String build(String publicBaseUrl, UploadKind kind, String storedName) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = "/uploads/" + kind.name().toLowerCase(Locale.ROOT) + "/" + storedName;
        return base.isBlank() ? path : base + path;
    }

    public static String normalize(String publicBaseUrl, String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }
        if (storedUrl.startsWith("http://") || storedUrl.startsWith("https://")) {
            return storedUrl;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            return storedUrl;
        }
        return storedUrl.startsWith("/") ? base + storedUrl : base + "/" + storedUrl;
    }
}
