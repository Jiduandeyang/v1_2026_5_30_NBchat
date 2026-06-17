package com.example.chat.voice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceServerConfigTest {
    @Test
    void includesStunAndOptionalTurnServer() {
        List<IceServerConfig.IceServer> servers = IceServerConfig.from(
                "stun:stun.l.google.com:19302",
                "turn:turn.example.com:3478",
                "user",
                "secret"
        );

        assertEquals(2, servers.size());
        assertEquals("stun:stun.l.google.com:19302", servers.get(0).urls());
        assertEquals("turn:turn.example.com:3478", servers.get(1).urls());
        assertEquals("user", servers.get(1).username());
        assertEquals("secret", servers.get(1).credential());
    }

    @Test
    void supportsMultipleTurnServersForMobileNetworkFallback() {
        List<IceServerConfig.IceServer> servers = IceServerConfig.from(
                "stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478",
                "turn:turn.example.com:3478,turns:turn.example.com:5349",
                "mobile-user",
                "mobile-secret"
        );

        assertEquals(4, servers.size());
        assertEquals("stun:stun.l.google.com:19302", servers.get(0).urls());
        assertEquals("stun:stun.cloudflare.com:3478", servers.get(1).urls());
        assertEquals("turn:turn.example.com:3478", servers.get(2).urls());
        assertEquals("turns:turn.example.com:5349", servers.get(3).urls());
        assertTrue(servers.subList(2, 4).stream()
                .allMatch(server -> "mobile-user".equals(server.username()) && "mobile-secret".equals(server.credential())));
    }

    @Test
    void omitsTurnWhenCredentialIsMissing() {
        List<IceServerConfig.IceServer> servers = IceServerConfig.from("stun:local", "turn:local", "", "");

        assertEquals(1, servers.size());
        assertTrue(servers.stream().noneMatch(server -> server.urls().startsWith("turn:")));
    }
}
