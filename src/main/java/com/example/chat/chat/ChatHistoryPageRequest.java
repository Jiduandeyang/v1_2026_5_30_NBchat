package com.example.chat.chat;

public record ChatHistoryPageRequest(int limit, Long beforeId) {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public static ChatHistoryPageRequest from(Integer limit, Long beforeId) {
        int normalizedLimit = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return new ChatHistoryPageRequest(normalizedLimit, beforeId);
    }
}
