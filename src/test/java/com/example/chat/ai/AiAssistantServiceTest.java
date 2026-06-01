package com.example.chat.ai;

import com.example.chat.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantServiceTest {
    @Test
    void detectsQianwenMentionOnlyWhenAssistantIsTagged() {
        AiAssistantService service = new AiAssistantService("千问小助手", null);

        assertTrue(service.isMentioned("@千问小助手 总结一下聊天"));
        assertTrue(service.isMentioned("请 @千问小助手 帮我回答"));
        assertFalse(service.isMentioned("千问小助手这个名字没有被@"));
        assertFalse(service.isMentioned("@其他助手 总结"));
    }

    @Test
    void extractsPromptAfterMention() {
        AiAssistantService service = new AiAssistantService("千问小助手", null);

        assertEquals("总结今天的重点", service.extractUserPrompt("@千问小助手 总结今天的重点"));
        assertEquals("请回答这个问题", service.extractUserPrompt("Bob: @千问小助手 请回答这个问题"));
        assertEquals("请根据最近聊天进行总结。", service.extractUserPrompt("@千问小助手"));
    }

    @Test
    void buildsPromptWithRecentConversationContext() {
        AiAssistantService service = new AiAssistantService("千问小助手", null);
        List<ChatMessage> history = List.of(
                new ChatMessage(1, 2, 1, "Alice", "TEXT", "明天九点开会",
                        null, null, null, null, null, java.util.List.of(), null, LocalDateTime.parse("2026-05-31T09:00:00")),
                new ChatMessage(2, 2, 2, "Bob", "TEXT", "我负责整理资料",
                        null, null, null, null, null, java.util.List.of(), null, LocalDateTime.parse("2026-05-31T09:01:00"))
        );

        String prompt = service.buildPrompt("总结任务安排", history);

        assertTrue(prompt.contains("总结任务安排"));
        assertTrue(prompt.contains("Alice: 明天九点开会"));
        assertTrue(prompt.contains("Bob: 我负责整理资料"));
        assertTrue(prompt.contains("中文"));
    }
}
