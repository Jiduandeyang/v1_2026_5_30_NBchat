package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupSettingsContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void schemaAndMigratorStorePerUserGroupSettings() throws IOException {
        String schema = read("src/main/resources/schema.sql");
        String migrator = read("src/main/java/com/example/chat/config/SchemaMigrator.java");

        assertTrue(schema.contains("remark VARCHAR(80)"));
        assertTrue(schema.contains("muted TINYINT(1) NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("background_key VARCHAR(40)"));
        assertTrue(schema.contains("background_url VARCHAR(255)"));
        assertTrue(migrator.contains("ensureConversationMembersGroupSettingColumns"));
        assertTrue(migrator.contains("ALTER TABLE conversation_members ADD COLUMN remark VARCHAR(80)"));
        assertTrue(migrator.contains("ALTER TABLE conversation_members ADD COLUMN muted TINYINT(1) NOT NULL DEFAULT 0"));
        assertTrue(migrator.contains("ALTER TABLE conversation_members ADD COLUMN background_key VARCHAR(40)"));
        assertTrue(migrator.contains("ALTER TABLE conversation_members ADD COLUMN background_url VARCHAR(255)"));
    }

    @Test
    void apiExposesGroupSettingsReadAndUpdate() throws IOException {
        String resource = read("src/main/java/com/example/chat/chat/ChatResource.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String dao = read("src/main/java/com/example/chat/chat/ChatDao.java");

        assertTrue(resource.contains("/groups/{conversationId}/settings"));
        assertTrue(resource.contains("groupSettings("));
        assertTrue(resource.contains("updateGroupSettings("));
        assertTrue(service.contains("normalizeGroupRemark"));
        assertTrue(service.contains("normalizeBackgroundKey"));
        assertTrue(service.contains("normalizeGroupBackgroundUrl"));
        assertTrue(dao.contains("groupSettings("));
        assertTrue(dao.contains("updateGroupSettings("));
        assertTrue(dao.contains("background_url"));
    }

    @Test
    void frontendWiresGroupSettingsIntoInspector() throws IOException {
        String html = read("src/main/webapp/app.html");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String css = read("src/main/webapp/assets/css/dashboard.css");

        assertTrue(html.contains("id=\"groupSettingsCard\""));
        assertTrue(html.contains("id=\"groupRemarkInput\""));
        assertTrue(html.contains("id=\"groupMutedInput\""));
        assertTrue(html.contains("id=\"groupBackgroundUploadInput\""));
        assertTrue(html.contains("id=\"uploadGroupBackgroundButton\""));
        assertTrue(html.contains("id=\"clearGroupBackgroundButton\""));
        assertTrue(html.contains("data-chat-background"));
        assertTrue(chatJs.contains("loadGroupSettings"));
        assertTrue(chatJs.contains("saveGroupSettings"));
        assertTrue(chatJs.contains("uploadGroupBackground"));
        assertTrue(chatJs.contains("backgroundUrl"));
        assertTrue(chatJs.contains("/chat/groups/${AppState.conversationId}/settings"));
        assertTrue(css.contains(".group-settings-card"));
        assertTrue(css.contains(".chat-background-soft-blue"));
        assertTrue(css.contains(".chat-background-custom"));
        assertTrue(css.contains("--chat-background-image"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
