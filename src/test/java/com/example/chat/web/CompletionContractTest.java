package com.example.chat.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void chatReadStateAndDeletedFriendGuardAreWired() throws IOException {
        String schema = read("src/main/resources/schema.sql");
        String dao = read("src/main/java/com/example/chat/chat/ChatDao.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String endpoint = read("src/main/java/com/example/chat/websocket/ChatEndpoint.java");
        String chatJs = read("src/main/webapp/assets/js/chat.js");

        assertTrue(schema.contains("conversation_reads"));
        assertTrue(dao.contains("markConversationRead"));
        assertTrue(dao.contains("last_read_message_id"));
        assertTrue(service.contains("ensurePrivateFriendshipCanSend"));
        assertTrue(endpoint.contains("\"ERROR\""));
        assertTrue(chatJs.contains("renderFailedMessage"));
    }

    @Test
    void momentsExposeUnlikeCommentsAndPaging() throws IOException {
        String resource = read("src/main/java/com/example/chat/moment/MomentResource.java");
        String dao = read("src/main/java/com/example/chat/moment/MomentDao.java");
        String momentsJs = read("src/main/webapp/assets/js/moments.js");

        assertTrue(resource.contains("@DELETE"));
        assertTrue(resource.contains("/{id}/likes"));
        assertTrue(resource.contains("/{id}/comments"));
        assertTrue(dao.contains("comments("));
        assertTrue(dao.contains("unlike("));
        assertTrue(dao.contains("LIMIT ?"));
        assertTrue(momentsJs.contains("loadMoreMoments"));
        assertTrue(momentsJs.contains("renderMomentComments"));
        assertTrue(momentsJs.contains("data-unlike"));
    }

    @Test
    void groupLeaveAndResponsiveInspectorAreAvailable() throws IOException {
        String resource = read("src/main/java/com/example/chat/chat/ChatResource.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String css = read("src/main/webapp/assets/css/dashboard.css");

        assertTrue(resource.contains("/groups/{conversationId}/leave"));
        assertTrue(service.contains("leaveGroup"));
        assertTrue(chatJs.contains("leaveGroupButton"));
        assertTrue(css.contains("clamp("));
        assertTrue(css.contains("container-type"));
    }

    @Test
    void chatColumnsCanBeResizedByDraggingSeparators() throws IOException {
        String html = read("src/main/webapp/app.html");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String css = read("src/main/webapp/assets/css/dashboard.css");

        assertTrue(html.contains("data-chat-resizer=\"left\""));
        assertTrue(html.contains("data-chat-resizer=\"right\""));
        assertTrue(chatJs.contains("initChatColumnResizers"));
        assertTrue(chatJs.contains("chatColumnWidths"));
        assertTrue(chatJs.contains("localStorage.setItem(\"chatColumnWidths\""));
        assertTrue(css.contains(".chat-resizer"));
        assertTrue(css.contains("--conversation-column"));
        assertTrue(css.contains("--inspector-column"));
    }

    @Test
    void finalReviewFixesAreWired() throws IOException {
        String resource = read("src/main/java/com/example/chat/chat/ChatResource.java");
        String dao = read("src/main/java/com/example/chat/chat/ChatDao.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String friendService = read("src/main/java/com/example/chat/friend/FriendService.java");
        String momentService = read("src/main/java/com/example/chat/moment/MomentService.java");
        String registry = read("src/main/java/com/example/chat/websocket/SocketRegistry.java");
        String chatEndpoint = read("src/main/java/com/example/chat/websocket/ChatEndpoint.java");
        String voiceEndpoint = read("src/main/java/com/example/chat/websocket/VoiceEndpoint.java");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String apiJs = read("src/main/webapp/assets/js/api.js");

        assertTrue(resource.contains("/messages/{messageId}/burn-read"));
        assertTrue(service.contains("markBurnMessageRead"));
        assertTrue(dao.contains("hideBurnMessageForUser"));
        assertTrue(chatJs.contains("markBurnMessageRead"));
        assertTrue(chatJs.contains("data-burn-message-id"));

        assertTrue(dao.contains("displayName"));
        assertFalse(service.contains("+ memberId +"));
        assertFalse(service.contains("+ userId + \""));

        assertTrue(dao.contains("messageById"));
        assertFalse(service.contains(".filter(m -> m.id() == id)"));
        assertFalse(service.contains("ChatHistoryPageRequest.from(100, null)"));

        assertTrue(service.contains("Transactional.withConnection"));
        assertTrue(friendService.contains("Transactional.withConnection"));
        assertTrue(momentService.contains("Transactional.withConnection"));
        assertFalse(service.contains("private interface SqlWork"));
        assertFalse(friendService.contains("private interface SqlWork"));
        assertFalse(momentService.contains("private interface SqlWork"));

        assertTrue(registry.contains("shared()"));
        assertTrue(registry.contains("ConcurrentHashMap<String"));
        assertTrue(chatEndpoint.contains("SocketRegistry.shared()"));
        assertTrue(chatEndpoint.contains("\"chat\""));
        assertTrue(voiceEndpoint.contains("SocketRegistry.shared()"));
        assertTrue(voiceEndpoint.contains("\"voice\""));

        assertFalse(chatJs.contains("$(\"#micButton\")?.addEventListener(\"click\""));
        assertFalse(apiJs.contains("window.toast"));
    }

    @Test
    void qqEmailRegistrationIsProductionReady() throws IOException {
        String validation = read("src/main/java/com/example/chat/common/Validation.java");
        String service = read("src/main/java/com/example/chat/auth/AuthService.java");
        String dao = read("src/main/java/com/example/chat/auth/AuthDao.java");
        String emailService = read("src/main/java/com/example/chat/auth/EmailCodeService.java");
        String mailSender = read("src/main/java/com/example/chat/auth/QqMailSender.java");
        String authJs = read("src/main/webapp/assets/js/auth.js");
        String exampleConfig = read("src/main/resources/app.properties.example");

        assertTrue(validation.contains("QQ_EMAIL_PATTERN"));
        assertTrue(service.contains("sendRegisterCode"));
        assertTrue(service.contains("emailExists"));
        assertTrue(service.contains("usernameExists"));
        assertTrue(service.contains("sendResetCode"));
        assertTrue(dao.contains("emailExists"));
        assertTrue(dao.contains("usernameExists"));
        assertTrue(dao.contains("markPreviousEmailCodesUsed"));
        assertTrue(emailService.contains("markPreviousEmailCodesUsed"));
        assertTrue(mailSender.contains("QQ 邮箱验证码"));
        assertTrue(authJs.contains("isQqEmail"));
        assertTrue(authJs.contains("@qq.com"));
        assertTrue(exampleConfig.contains("mail.devMode=false"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
