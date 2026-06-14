package com.example.chat.admin;

public record DashboardStats(
        long totalUsers,
        long totalMessages,
        long totalGroups,
        long totalMoments,
        long activeUsersToday,
        long newUsersThisWeek,
        long messagesToday,
        long disabledUsers
) {
}
