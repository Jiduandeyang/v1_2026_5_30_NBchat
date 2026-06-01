package com.example.chat.ai;

import com.example.chat.config.AppConfig;
import com.example.chat.model.ChatMessage;

import java.util.List;

public class AiAssistantService {
    private static final String DEFAULT_PROMPT = "请根据最近聊天进行总结。";

    private final String assistantName;
    private final DeepSeekClient deepSeekClient;

    public AiAssistantService() {
        this(AppConfig.get("ai.assistantName", "千问小助手"), DeepSeekClient.fromConfig());
    }

    public AiAssistantService(String assistantName, DeepSeekClient deepSeekClient) {
        this.assistantName = assistantName == null || assistantName.isBlank() ? "千问小助手" : assistantName;
        this.deepSeekClient = deepSeekClient;
    }

    public boolean isMentioned(String content) {
        return content != null && content.contains("@" + assistantName);
    }

    public String extractUserPrompt(String content) {
        if (content == null) return DEFAULT_PROMPT;
        int mentionIndex = content.indexOf("@" + assistantName);
        if (mentionIndex < 0) return content.trim().isBlank() ? DEFAULT_PROMPT : content.trim();
        String prompt = content.substring(mentionIndex + assistantName.length() + 1).trim();
        return prompt.isBlank() ? DEFAULT_PROMPT : prompt;
    }

    public String buildPrompt(String userPrompt, List<ChatMessage> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是群聊中的中文智能助手，名字是").append(assistantName).append("。");
        builder.append("请基于群聊上下文回答用户问题，回答要准确、简洁、可执行。");
        builder.append("\n\n用户请求：").append(userPrompt == null || userPrompt.isBlank() ? DEFAULT_PROMPT : userPrompt.trim());
        builder.append("\n\n最近聊天记录：\n");
        List<ChatMessage> recent = history == null ? List.of() : history.stream()
                .skip(Math.max(0, history.size() - 30))
                .toList();
        for (ChatMessage message : recent) {
            builder.append(message.senderName()).append(": ").append(message.content()).append('\n');
        }
        return builder.toString();
    }

    public String answer(String mentionMessage, List<ChatMessage> history) {
        String userPrompt = extractUserPrompt(mentionMessage);
        String prompt = buildPrompt(userPrompt, history);
        if (deepSeekClient == null) {
            return "AI 助手未配置 DeepSeek 客户端。";
        }
        return deepSeekClient.chat("你是一个在 Jakarta Chat 群聊中工作的中文助手。", prompt);
    }
}
