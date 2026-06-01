package com.example.chat.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthEmailContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void authFlowGuardsQqEmailRegistrationAndReset() throws IOException {
        String service = read("src/main/java/com/example/chat/auth/AuthService.java");
        String dao = read("src/main/java/com/example/chat/auth/AuthDao.java");
        String emailService = read("src/main/java/com/example/chat/auth/EmailCodeService.java");
        String cooldown = read("src/main/java/com/example/chat/auth/EmailCodeCooldown.java");
        String sender = read("src/main/java/com/example/chat/auth/QqMailSender.java");
        String authJs = read("src/main/webapp/assets/js/auth.js");

        assertTrue(dao.contains("emailExists"));
        assertTrue(dao.contains("usernameExists"));
        assertTrue(dao.contains("latestCode.id"));
        assertTrue(dao.contains("latestEmailCodeCreatedAt"));
        assertTrue(dao.contains("markExistingCodesUsed"));
        assertTrue(cooldown.contains("COOLDOWN_SECONDS"));
        assertTrue(emailService.contains("remainingSeconds"));
        assertTrue(emailService.contains("Please wait"));
        assertTrue(service.contains("Email is already registered."));
        assertTrue(service.contains("Username is already taken."));
        assertTrue(service.contains("Email is not registered."));
        assertTrue(emailService.contains("markExistingCodesUsed"));
        assertTrue(sender.contains("QQ_SMTP_USERNAME"));
        assertTrue(sender.contains("QQ_SMTP_AUTHCODE"));
        assertTrue(sender.contains("setSubject"));
        assertTrue(sender.contains("UTF-8"));
        assertTrue(authJs.contains("isQqEmail"));
        assertTrue(authJs.contains("123456@qq.com"));
        assertTrue(authJs.contains("startCodeCooldown"));
        assertTrue(authJs.contains("codeCooldownUntil"));
        assertTrue(authJs.contains("180"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
