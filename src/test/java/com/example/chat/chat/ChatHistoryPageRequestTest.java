package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatHistoryPageRequestTest {
    @Test
    void clampsLimitAndKeepsBeforeIdOptional() {
        ChatHistoryPageRequest request = ChatHistoryPageRequest.from(500, 42L);

        assertEquals(100, request.limit());
        assertEquals(42L, request.beforeId());
    }

    @Test
    void usesDefaultLimitForMissingOrInvalidInput() {
        assertEquals(50, ChatHistoryPageRequest.from(null, null).limit());
        assertEquals(50, ChatHistoryPageRequest.from(0, null).limit());
        assertEquals(50, ChatHistoryPageRequest.from(-3, null).limit());
    }
}
