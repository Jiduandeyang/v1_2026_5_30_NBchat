package com.example.chat.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedDataTest {
    @Test
    void schemaContainsDemoUsersAndAdminRole() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(schema.contains("role VARCHAR(20) NOT NULL DEFAULT 'USER'"));
            assertTrue(schema.contains("'alice'"));
            assertTrue(schema.contains("'bob'"));
            assertTrue(schema.contains("'admin'"));
            assertTrue(schema.contains("'ADMIN'"));
        }
    }
}
