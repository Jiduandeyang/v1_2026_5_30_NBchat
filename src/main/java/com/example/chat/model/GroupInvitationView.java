package com.example.chat.model;

import java.time.LocalDateTime;

public record GroupInvitationView(
        long id,
        long groupId,
        long conversationId,
        String groupName,
        long inviterId,
        String inviterName,
        long inviteeId,
        String inviteeName,
        String status,
        LocalDateTime createdAt
) {
}
