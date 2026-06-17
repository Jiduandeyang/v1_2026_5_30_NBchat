package com.example.chat.voice;

public record VoiceCallStartRequest(Long conversationId, String callMode) {
    public String normalizedCallMode() {
        return "video".equalsIgnoreCase(callMode) ? "video" : "audio";
    }
}
