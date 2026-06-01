package com.example.chat.websocket;

import com.example.chat.chat.ChatService;
import com.example.chat.chat.SendMessageRequest;
import com.example.chat.common.SessionKeys;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.RecallUpdate;
import com.example.chat.model.ReactionUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/ws/chat", configurator = HttpSessionConfigurator.class)
public class ChatEndpoint {
    private static final String CHANNEL = "chat";
    private static final SocketRegistry REGISTRY = SocketRegistry.shared();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final ChatService CHAT_SERVICE = new ChatService();

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
        JsonNode payload = JSON.readTree(raw);
        try {
            if ("REACTION".equals(payload.path("event").asText())) {
                handleReaction(userId, payload);
                return;
            }
            if ("RECALL".equals(payload.path("event").asText())) {
                handleRecall(userId, payload);
                return;
            }
            SendMessageRequest request = JSON.readValue(raw, SendMessageRequest.class);
            for (ChatMessage message : CHAT_SERVICE.sendWithAssistantReplies(userId, request)) {
                String json = JSON.writeValueAsString(message);
                REGISTRY.send(CHANNEL, userId, json);
                for (Long recipient : CHAT_SERVICE.recipients(request.conversationId(), userId)) {
                    REGISTRY.send(CHANNEL, recipient, json);
                }
            }
        } catch (Exception exception) {
            REGISTRY.send(CHANNEL, userId, JSON.writeValueAsString(java.util.Map.of(
                    "event", "ERROR",
                    "conversationId", payload.path("conversationId").asLong(0),
                    "content", payload.path("content").asText(""),
                    "message", exception.getMessage() == null ? "消息发送失败。" : exception.getMessage()
            )));
        }
    }

    private void handleReaction(long userId, JsonNode payload) throws Exception {
        long messageId = payload.path("messageId").asLong();
        String emoji = payload.path("emoji").asText();
        boolean remove = payload.path("remove").asBoolean(false);
        ReactionUpdate update = remove
                ? CHAT_SERVICE.removeReactionUpdate(userId, messageId, emoji)
                : CHAT_SERVICE.addReactionUpdate(userId, messageId, emoji);
        String json = JSON.writeValueAsString(update);
        REGISTRY.send(CHANNEL, userId, json);
        for (Long recipient : CHAT_SERVICE.recipients(update.conversationId(), userId)) {
            REGISTRY.send(CHANNEL, recipient, json);
        }
    }

    private void handleRecall(long userId, JsonNode payload) throws Exception {
        RecallUpdate update = CHAT_SERVICE.recallMessage(userId, payload.path("messageId").asLong());
        String json = JSON.writeValueAsString(update);
        REGISTRY.send(CHANNEL, userId, json);
        for (Long recipient : CHAT_SERVICE.recipients(update.conversationId(), userId)) {
            REGISTRY.send(CHANNEL, recipient, json);
        }
    }

    @OnClose
    public void close(Session socket) {
        Object userId = socket.getUserProperties().get(SessionKeys.USER_ID);
        if (userId instanceof Number number) {
            REGISTRY.remove(CHANNEL, number.longValue(), socket);
        }
    }
}
