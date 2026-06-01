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
    void omitsTurnWhenCredentialIsMissing() {
        List<IceServerConfig.IceServer> servers = IceServerConfig.from("stun:local", "turn:local", "", "");

        assertEquals(1, servers.size());
        assertTrue(servers.stream().noneMatch(server -> server.urls().startsWith("turn:")));
    }
}
