package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeChatFeaturesContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void timeCapsuleMessagesPersistUnlockTimeAndExposeItToClients() throws IOException {
        String schema = read("src/main/resources/schema.sql");
        String dao = read("src/main/java/com/example/chat/chat/ChatDao.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String message = read("src/main/java/com/example/chat/model/ChatMessage.java");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String html = read("src/main/webapp/app.html");

        assertTrue(schema.contains("unlock_at TIMESTAMP NULL"));
        assertTrue(dao.contains("message.unlockAtDateTime()"));
        assertTrue(dao.contains("m.unlock_at"));
        assertTrue(service.contains("normalizeTimeCapsuleRequest"));
        assertTrue(message.contains("LocalDateTime unlockAt"));
        assertTrue(chatJs.contains("message.type === \"TIME_CAPSULE\""));
        assertTrue(chatJs.contains("renderTimeCapsuleMessage"));
        assertTrue(chatJs.contains("timeCapsuleButton"));
        assertTrue(html.contains("id=\"timeCapsuleButton\""));
    }

    @Test
    void moodWeatherEndpointAndPanelArePresent() throws IOException {
        String resource = read("src/main/java/com/example/chat/chat/ChatResource.java");
        String service = read("src/main/java/com/example/chat/chat/ChatService.java");
        String dao = read("src/main/java/com/example/chat/chat/ChatDao.java");
        String model = read("src/main/java/com/example/chat/model/ConversationMoodWeather.java");
        String chatJs = read("src/main/webapp/assets/js/chat.js");
        String html = read("src/main/webapp/app.html");
        String css = read("src/main/webapp/assets/css/dashboard.css");

        assertTrue(resource.contains("/conversations/{id}/mood-weather"));
        assertTrue(service.contains("moodWeather"));
        assertTrue(service.contains("buildMoodWeather"));
        assertTrue(dao.contains("recentMessagesForMood"));
        assertTrue(model.contains("record ConversationMoodWeather"));
        assertTrue(chatJs.contains("loadMoodWeather"));
        assertTrue(chatJs.contains("renderMoodWeather"));
        assertTrue(html.contains("id=\"moodWeatherCard\""));
        assertTrue(css.contains(".mood-weather-card"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
