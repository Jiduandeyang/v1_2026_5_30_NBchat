package com.example.chat.auth;

public record PasswordResetRequest(String qqEmail, String code, String newPassword) {
}
