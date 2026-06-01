package com.example.chat.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusyUserRegistryTest {
    @Test
    void reservesAndReleasesBothCallParticipants() {
        BusyUserRegistry registry = new BusyUserRegistry();

        assertTrue(registry.reserve(1L, 2L));
        assertFalse(registry.reserve(2L, 3L));
        registry.release(1L, 2L);
        assertTrue(registry.reserve(2L, 3L));
    }
}
