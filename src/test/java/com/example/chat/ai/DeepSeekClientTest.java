package com.example.chat.ai;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {
    @Test
    void postsChatCompletionsRequestAndExtractsAssistantMessage() {
        DeepSeekClient.Transport transport = (uri, json, timeout) -> {
            assertEquals(URI.create("https://api.deepseek.com/chat/completions"), uri);
            assertEquals(Duration.ofSeconds(45), timeout);
            assertTrue(json.contains("\"model\":\"deepseek-chat\""));
            assertTrue(json.contains("\"stream\":false"));
            assertTrue(json.contains("\"role\":\"system\""));
            assertTrue(json.contains("\"role\":\"user\""));
            return """
                    {"choices":[{"message":{"role":"assistant","content":"这是总结结果。"}}]}
                    """;
        };
        DeepSeekClient client = new DeepSeekClient("https://api.deepseek.com", "test-key", "deepseek-chat", transport);

        assertEquals("这是总结结果。", client.chat("系统提示", "用户问题"));
    }

    @Test
    void returnsFallbackWhenExternalApiIsUnavailable() {
        DeepSeekClient.Transport transport = (uri, json, timeout) -> {
            throw new java.io.IOException("connection refused");
        };
        DeepSeekClient client = new DeepSeekClient("https://api.deepseek.com", "test-key", "deepseek-chat", transport);

        String reply = client.chat("系统提示", "用户问题");

        assertTrue(reply.contains("DeepSeek AI 助手暂时不可用"));
    }
}
