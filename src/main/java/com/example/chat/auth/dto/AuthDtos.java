package com.example.chat.auth.dto;

import com.example.chat.model.User;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record EmailCodeRequest(String qqEmail) {
    }

    public record RegisterRequest(String username, String password, String qqEmail, String code, String nickname) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record ResetPasswordRequest(String qqEmail, String code, String newPassword) {
    }

    public record SessionUser(User user) {
    }
}
