package com.example.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {
    @Test
    void loadsUtf8Properties() {
        assertEquals("千问小助手", AppConfig.get("ai.assistantName", ""));
    }
}
