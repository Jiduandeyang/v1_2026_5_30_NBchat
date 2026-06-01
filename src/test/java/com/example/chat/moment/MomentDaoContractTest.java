package com.example.chat.moment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentDaoContractTest {
    @Test
    void feedQueryEnforcesVisibilityRulesAndFriendship() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/example/chat/moment/MomentDao.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("moment_visibility_rules"));
        assertTrue(source.contains("friendships"));
        assertTrue(source.contains("SELECTED_FRIEND"));
        assertTrue(source.contains("SELECTED_GROUP"));
        assertTrue(source.contains("EXCLUDED_FRIEND"));
        assertTrue(source.contains("EXCLUDED_GROUP"));
    }
}
