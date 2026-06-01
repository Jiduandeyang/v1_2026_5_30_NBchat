package com.example.chat.model;

import java.util.List;

public record ReactionUpdate(String event, long conversationId, long messageId, List<MessageReactionSummary> reactions) {
    public static ReactionUpdate of(long conversationId, long messageId, List<MessageReactionSummary> reactions) {
        return new ReactionUpdate("REACTION", conversationId, messageId, reactions);
    }
}
