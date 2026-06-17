package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGroupManagementContractTest {
    @Test
    void resourceExposesGroupManagementEndpoints() throws IOException {
        String resource = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatResource.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(resource.contains("/groups/{conversationId}/members"));
        assertTrue(resource.contains("/groups/{conversationId}/name"));
        assertTrue(resource.contains("/groups/{conversationId}/members/{memberId}/role"));
        assertTrue(resource.contains("/groups/{conversationId}/members/{memberId}"));
    }

    @Test
    void groupInvitationsNotifyInviteesInRealtime() throws IOException {
        String service = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatService.java"),
                StandardCharsets.UTF_8
        );
        String chat = Files.readString(
                Path.of("src/main/webapp/assets/js/chat.js"),
                StandardCharsets.UTF_8
        );

        assertTrue(service.contains("SocketRegistry.shared()"));
        assertTrue(service.contains("\"GROUP_INVITATION\""));
        assertTrue(service.contains("REGISTRY.send(\"chat\", memberId"));
        assertTrue(chat.contains("message.event === \"GROUP_INVITATION\""));
        assertTrue(chat.contains("window.loadRequests?.()"));
        assertTrue(chat.contains("switchView(\"friendsView\")"));
        assertTrue(chat.contains("普通好友已收到群邀请"));
    }

    @Test
    void resourceExposesRichMessagingEndpoints() throws IOException {
        String resource = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatResource.java"),
                StandardCharsets.UTF_8
        );
        String schema = Files.readString(
                Path.of("src/main/resources/schema.sql"),
                StandardCharsets.UTF_8
        );
        String endpoint = Files.readString(
                Path.of("src/main/java/com/example/chat/websocket/ChatEndpoint.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(resource.contains("/messages/{messageId}/reactions"));
        assertTrue(resource.contains("/messages/{messageId}/reactions/{emoji}"));
        assertTrue(endpoint.contains("\"REACTION\""));
        assertTrue(schema.contains("reply_to_message_id"));
        assertTrue(schema.contains("message_reactions"));
    }

    @Test
    void resourceRemovesChatHeatmapEndpointInFavorOfMoodWeather() throws IOException {
        String resource = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatResource.java"),
                StandardCharsets.UTF_8
        );
        String dao = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatDao.java"),
                StandardCharsets.UTF_8
        );

        assertFalse(resource.contains("/conversations/{id}/heatmap"));
        assertFalse(dao.contains("dailyMessageCounts"));
        assertTrue(resource.contains("/conversations/{id}/mood-weather"));
    }

    @Test
    void resourceExposesMessageRecallContract() throws IOException {
        String resource = Files.readString(
                Path.of("src/main/java/com/example/chat/chat/ChatResource.java"),
                StandardCharsets.UTF_8
        );
        String schema = Files.readString(
                Path.of("src/main/resources/schema.sql"),
                StandardCharsets.UTF_8
        );
        String endpoint = Files.readString(
                Path.of("src/main/java/com/example/chat/websocket/ChatEndpoint.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(resource.contains("/messages/{messageId}/recall"));
        assertTrue(schema.contains("recalled_at"));
        assertTrue(endpoint.contains("\"RECALL\""));
    }
}
