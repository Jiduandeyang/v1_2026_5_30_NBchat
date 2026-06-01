package com.example.chat.user;

public record ProfileUpdateRequest(String nickname, String signature, String avatarUrl, String backgroundUrl) {
}
