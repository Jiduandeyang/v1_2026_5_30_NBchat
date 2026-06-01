package com.example.chat.voice;

public record VoiceSignal(long callId, long targetUserId, String type, String payload) {
}
