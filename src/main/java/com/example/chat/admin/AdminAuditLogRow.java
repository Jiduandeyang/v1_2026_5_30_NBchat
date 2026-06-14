package com.example.chat.admin;

import java.time.LocalDateTime;

public record AdminAuditLogRow(
        long id,
        long adminId,
        String adminName,
        String action,
        String targetType,
        Long targetId,
        String detail,
        LocalDateTime createdAt
) {
}
