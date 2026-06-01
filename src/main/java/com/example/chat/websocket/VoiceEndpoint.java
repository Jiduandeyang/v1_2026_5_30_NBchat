package com.example.chat.websocket;

import com.example.chat.common.SessionKeys;
import com.example.chat.voice.VoiceSignal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/ws/voice", configurator = HttpSessionConfigurator.class)
public class VoiceEndpoint {
    private static final String CHANNEL = "voice";
    private static final SocketRegistry REGISTRY = SocketRegistry.shared();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @OnOpen
    public void open(Session socket, EndpointConfig config) throws Exception {
        HttpSession httpSession = (HttpSession) config.getUserProperties().get(HttpSessionConfigurator.HTTP_SESSION);
        if (httpSession == null || httpSession.getAttribute(SessionKeys.USER_ID) == null) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Login required"));
            return;
        }
        long userId = ((Number) httpSession.getAttribute(SessionKeys.USER_ID)).longValue();
        socket.getUserProperties().put(SessionKeys.USER_ID, userId);
        REGISTRY.add(CHANNEL, userId, socket);
    }

    @OnMessage
    public void message(Session socket, String raw) throws Exception {
        long userId = ((Number) socket.getUserProperties().get(SessionKeys.USER_ID)).longValue();
        VoiceSignal signal = JSON.readValue(raw, VoiceSignal.class);
        String json = JSON.writeValueAsString(new RoutedVoiceSignal(userId, signal.callId(), signal.type(), signal.payload()));
        REGISTRY.send(CHANNEL, signal.targetUserId(), json);
    }

    @OnClose
    public void close(Session socket) {
        Object userId = socket.getUserProperties().get(SessionKeys.USER_ID);
        if (userId instanceof Number number) {
            REGISTRY.remove(CHANNEL, number.longValue(), socket);
        }
    }

    public record RoutedVoiceSignal(long fromUserId, long callId, String type, String payload) {
    }
}
