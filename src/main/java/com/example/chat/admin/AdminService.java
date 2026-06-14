package com.example.chat.admin;

import com.example.chat.common.AppException;
import com.example.chat.common.Transactional;

public class AdminService {
    private final AdminDao adminDao = new AdminDao();

    public DashboardStats dashboard() {
        return Transactional.withConnection(adminDao::dashboard);
    }

    public AdminPage<AdminUserRow> users(String q, int page, int size) {
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        return Transactional.withConnection(connection -> new AdminPage<>(
                adminDao.users(connection, q, normalizedPage, normalizedSize),
                normalizedPage,
                normalizedSize,
                adminDao.countUsers(connection, q)
        ));
    }

    public void setUserRole(long adminId, long userId, String role) {
        String normalizedRole = "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER";
        Transactional.run(connection -> {
            adminDao.setUserRole(connection, userId, normalizedRole);
            adminDao.logAction(connection, adminId, "SET_USER_ROLE", "USER", userId, "role=" + normalizedRole);
        });
    }

    public void setUserDisabled(long adminId, long userId, boolean disabled) {
        if (adminId == userId && disabled) {
            throw AppException.badRequest("You cannot disable yourself.");
        }
        Transactional.run(connection -> {
            adminDao.setUserDisabled(connection, userId, disabled);
            adminDao.logAction(connection, adminId, disabled ? "DISABLE_USER" : "ENABLE_USER", "USER", userId, "disabled=" + disabled);
        });
    }

    public AdminPage<AdminGroupRow> groups(int page, int size) {
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        return Transactional.withConnection(connection -> new AdminPage<>(
                adminDao.groups(connection, normalizedPage, normalizedSize),
                normalizedPage,
                normalizedSize,
                adminDao.countGroups(connection)
        ));
    }

    public void disbandGroup(long adminId, long conversationId) {
        Transactional.run(connection -> {
            adminDao.disbandGroup(connection, conversationId);
            adminDao.logAction(connection, adminId, "DISBAND_GROUP", "CONVERSATION", conversationId, "conversationId=" + conversationId);
        });
    }

    public AdminPage<AdminMomentRow> moments(int page, int size) {
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        return Transactional.withConnection(connection -> new AdminPage<>(
                adminDao.moments(connection, normalizedPage, normalizedSize),
                normalizedPage,
                normalizedSize,
                adminDao.countMoments(connection)
        ));
    }

    public void deleteMoment(long adminId, long momentId) {
        Transactional.run(connection -> {
            adminDao.deleteMoment(connection, momentId);
            adminDao.logAction(connection, adminId, "DELETE_MOMENT", "MOMENT", momentId, "momentId=" + momentId);
        });
    }

    public AdminPage<AdminAuditLogRow> auditLogs(int page, int size) {
        int normalizedPage = page(page);
        int normalizedSize = size(size);
        return Transactional.withConnection(connection -> new AdminPage<>(
                adminDao.auditLogs(connection, normalizedPage, normalizedSize),
                normalizedPage,
                normalizedSize,
                adminDao.countAuditLogs(connection)
        ));
    }

    private int page(int page) {
        return Math.max(1, page);
    }

    private int size(int size) {
        return Math.max(1, Math.min(50, size));
    }
}
