package com.example.chat.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationTest {
    @Test
    void exposesConversationPreviewAndPeerMetadata() {
        LocalDateTime sentAt = LocalDateTime.parse("2026-05-30T10:30:00");
        Conversation conversation = new Conversation(
                7L,
                "PRIVATE",
                "Bob",
                2L,
                "Bob",
                "/uploads/avatar/bob.png",
                "Latest message",
                "TEXT",
                sentAt,
                3
        );

        assertEquals(2L, conversation.peerId());
        assertEquals("Bob", conversation.peerName());
        assertEquals("Latest message", conversation.lastMessage());
        assertEquals(sentAt, conversation.lastSentAt());
        assertEquals(3, conversation.unreadCount());
    }
}
