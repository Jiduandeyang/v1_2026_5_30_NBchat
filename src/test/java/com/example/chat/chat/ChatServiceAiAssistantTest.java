package com.example.chat.chat;

import com.example.chat.ai.AiAssistantService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceAiAssistantTest {
    @Test
    void triggersAssistantOnlyForGroupMentions() {
        ChatService service = new ChatService(new AiAssistantService("千问小助手", null));

        assertTrue(service.shouldTriggerAssistant("GROUP", "@千问小助手 总结群聊"));
        assertFalse(service.shouldTriggerAssistant("PRIVATE", "@千问小助手 总结私聊"));
        assertFalse(service.shouldTriggerAssistant("GROUP", "普通群聊消息"));
    }
}
