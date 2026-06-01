package com.example.chat.websocket;

import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SocketRegistry {
    private static final SocketRegistry SHARED = new SocketRegistry();

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Set<Session>>> sessions = new ConcurrentHashMap<>();

    public static SocketRegistry shared() {
        return SHARED;
    }

    public void add(String channel, long userId, Session session) {
        channelSessions(channel).computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(String channel, long userId, Session session) {
        Set<Session> userSessions = channelSessions(channel).get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
        }
    }

    public void send(String channel, long userId, String json) {
        Set<Session> userSessions = channelSessions(channel).get(userId);
        if (userSessions == null) {
            return;
        }
        for (Session session : userSessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException ignored) {
                    // A broken socket should not prevent delivery to other sessions.
                }
            }
        }
    }

    private ConcurrentHashMap<Long, Set<Session>> channelSessions(String channel) {
        return sessions.computeIfAbsent(channel, ignored -> new ConcurrentHashMap<>());
    }
}
