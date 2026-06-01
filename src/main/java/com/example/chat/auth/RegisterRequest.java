package com.example.chat.auth;

public record RegisterRequest(String username, String password, String qqEmail, String code, String nickname) {
}
