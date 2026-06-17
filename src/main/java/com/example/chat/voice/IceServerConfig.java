package com.example.chat.voice;

import java.util.ArrayList;
import java.util.List;

public final class IceServerConfig {
    private IceServerConfig() {
    }

    public static List<IceServer> from(String stunUrls, String turnUrl, String turnUsername, String turnCredential) {
        List<IceServer> servers = new ArrayList<>();
        for (String stunUrl : split(stunUrls)) {
            servers.add(new IceServer(stunUrl, null, null));
        }
        if (turnUrl != null && !turnUrl.isBlank() &&
                turnUsername != null && !turnUsername.isBlank() &&
                turnCredential != null && !turnCredential.isBlank()) {
            for (String url : split(turnUrl)) {
                servers.add(new IceServer(url, turnUsername.trim(), turnCredential.trim()));
            }
        }
        return List.copyOf(servers);
    }

    private static List<String> split(String urls) {
        if (urls == null || urls.isBlank()) {
            return List.of();
        }
        return List.of(urls.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record IceServer(String urls, String username, String credential) {
    }
}
