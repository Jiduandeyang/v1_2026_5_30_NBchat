package com.example.chat.model;

import java.util.List;

public record ConversationMoodWeather(
        String code,
        String title,
        String summary,
        String suggestion,
        int energy,
        int messageCount,
        List<String> signals
) {
}
