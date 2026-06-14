package com.example.chat.admin;

import com.example.chat.common.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class AdminDao {
    public DashboardStats dashboard(Connection connection) throws SQLException {
        return Jdbc.one(connection, """
                SELECT
                    (SELECT COUNT(*) FROM users) total_users,
                    (SELECT COUNT(*) FROM messages) total_messages,
                    (SELECT COUNT(*) FROM chat_groups) total_groups,
                    (SELECT COUNT(*) FROM moments WHERE deleted=0) total_moments,
                    (SELECT COUNT(DISTINCT sender_id) FROM messages WHERE sent_at>=CURDATE()) active_users_today,
                    (SELECT COUNT(*) FROM users WHERE created_at>=DATE_SUB(CURDATE(), INTERVAL 7 DAY)) new_users_this_week,
                    (SELECT COUNT(*) FROM messages WHERE sent_at>=CURDATE()) messages_today,
                    (SELECT COUNT(*) FROM users WHERE disabled=1) disabled_users
                """,
                ps -> {
                },
                rs -> new DashboardStats(rs.getLong("total_users"), rs.getLong("total_messages"),
                        rs.getLong("total_groups"), rs.getLong("total_moments"), rs.getLong("active_users_today"),
                        rs.getLong("new_users_this_week"), rs.getLong("messages_today"), rs.getLong("disabled_users")));
    }

    public List<AdminUserRow> users(Connection connection, String q, int page, int size) throws SQLException {
        String like = like(q);
        return Jdbc.list(connection,
                "SELECT id,username,qq_email,nickname,role,disabled,created_at FROM users " +
                        "WHERE username LIKE ? OR qq_email LIKE ? OR nickname LIKE ? ORDER BY id LIMIT ? OFFSET ?",
                ps -> {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    ps.setInt(4, size);
                    ps.setInt(5, offset(page, size));
                },
                rs -> new AdminUserRow(rs.getLong("id"), rs.getString("username"), rs.getString("qq_email"),
                        rs.getString("nickname"), rs.getString("role"), rs.getBoolean("disabled"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public long countUsers(Connection connection, String q) throws SQLException {
        String like = like(q);
        Long total = Jdbc.one(connection,
                "SELECT COUNT(*) total FROM users WHERE username LIKE ? OR qq_email LIKE ? OR nickname LIKE ?",
                ps -> {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                },
                rs -> rs.getLong("total"));
        return total == null ? 0 : total;
    }

    public void setUserRole(Connection connection, long userId, String role) throws SQLException {
        Jdbc.update(connection, "UPDATE users SET role=? WHERE id=?", ps -> {
            ps.setString(1, role);
            ps.setLong(2, userId);
        });
    }

    public void setUserDisabled(Connection connection, long userId, boolean disabled) throws SQLException {
        Jdbc.update(connection, "UPDATE users SET disabled=? WHERE id=?", ps -> {
            ps.setBoolean(1, disabled);
            ps.setLong(2, userId);
        });
    }

    public List<AdminGroupRow> groups(Connection connection, int page, int size) throws SQLException {
        return Jdbc.list(connection,
                "SELECT cg.id group_id,cg.conversation_id,cg.name,COALESCE(u.nickname,u.username) owner_name,cg.created_at," +
                        "(SELECT COUNT(*) FROM chat_group_members gm WHERE gm.group_id=cg.id) member_count," +
                        "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id=cg.conversation_id) message_count " +
                        "FROM chat_groups cg JOIN users u ON u.id=cg.owner_id ORDER BY cg.created_at DESC LIMIT ? OFFSET ?",
                ps -> {
                    ps.setInt(1, size);
                    ps.setInt(2, offset(page, size));
                },
                rs -> new AdminGroupRow(rs.getLong("group_id"), rs.getLong("conversation_id"), rs.getString("name"),
                        rs.getString("owner_name"), rs.getInt("member_count"), rs.getInt("message_count"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public long countGroups(Connection connection) throws SQLException {
        Long total = Jdbc.one(connection, "SELECT COUNT(*) total FROM chat_groups", ps -> {
        }, rs -> rs.getLong("total"));
        return total == null ? 0 : total;
    }

    public void disbandGroup(Connection connection, long conversationId) throws SQLException {
        Long groupId = Jdbc.one(connection, "SELECT id FROM chat_groups WHERE conversation_id=?",
                ps -> ps.setLong(1, conversationId), rs -> rs.getLong("id"));
        if (groupId == null) {
            return;
        }
        Jdbc.update(connection, "DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE conversation_id=?)",
                ps -> ps.setLong(1, conversationId));
        Jdbc.update(connection, "DELETE FROM message_visibility WHERE message_id IN (SELECT id FROM messages WHERE conversation_id=?)",
                ps -> ps.setLong(1, conversationId));
        Jdbc.update(connection, "UPDATE messages SET reply_to_message_id=NULL WHERE conversation_id=?",
                ps -> ps.setLong(1, conversationId));
        Jdbc.update(connection, "DELETE FROM messages WHERE conversation_id=?", ps -> ps.setLong(1, conversationId));
        Jdbc.update(connection, "DELETE FROM chat_group_invitations WHERE group_id=?", ps -> ps.setLong(1, groupId));
        Jdbc.update(connection, "DELETE FROM chat_group_members WHERE group_id=?", ps -> ps.setLong(1, groupId));
        Jdbc.update(connection, "DELETE FROM conversation_members WHERE conversation_id=?", ps -> ps.setLong(1, conversationId));
        Jdbc.update(connection, "DELETE FROM chat_groups WHERE id=?", ps -> ps.setLong(1, groupId));
        Jdbc.update(connection, "DELETE FROM conversations WHERE id=?", ps -> ps.setLong(1, conversationId));
    }

    public List<AdminMomentRow> moments(Connection connection, int page, int size) throws SQLException {
        return Jdbc.list(connection,
                "SELECT m.id,m.author_id,COALESCE(u.nickname,u.username) author_name,m.text,m.visibility,m.deleted,m.created_at," +
                        "(SELECT COUNT(*) FROM moment_media mm WHERE mm.moment_id=m.id) media_count," +
                        "(SELECT COUNT(*) FROM moment_likes ml WHERE ml.moment_id=m.id) like_count," +
                        "(SELECT COUNT(*) FROM moment_comments mc WHERE mc.moment_id=m.id) comment_count " +
                        "FROM moments m JOIN users u ON u.id=m.author_id ORDER BY m.id DESC LIMIT ? OFFSET ?",
                ps -> {
                    ps.setInt(1, size);
                    ps.setInt(2, offset(page, size));
                },
                rs -> new AdminMomentRow(rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"),
                        rs.getString("text"), rs.getString("visibility"), rs.getBoolean("deleted"),
                        rs.getInt("media_count"), rs.getInt("like_count"), rs.getInt("comment_count"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public long countMoments(Connection connection) throws SQLException {
        Long total = Jdbc.one(connection, "SELECT COUNT(*) total FROM moments", ps -> {
        }, rs -> rs.getLong("total"));
        return total == null ? 0 : total;
    }

    public void deleteMoment(Connection connection, long momentId) throws SQLException {
        Jdbc.update(connection, "UPDATE moments SET deleted=1 WHERE id=?", ps -> ps.setLong(1, momentId));
    }

    public List<AdminAuditLogRow> auditLogs(Connection connection, int page, int size) throws SQLException {
        return Jdbc.list(connection,
                "SELECT l.id,l.admin_id,COALESCE(u.nickname,u.username) admin_name,l.action,l.target_type,l.target_id,l.detail,l.created_at " +
                        "FROM admin_audit_logs l JOIN users u ON u.id=l.admin_id ORDER BY l.id DESC LIMIT ? OFFSET ?",
                ps -> {
                    ps.setInt(1, size);
                    ps.setInt(2, offset(page, size));
                },
                rs -> {
                    long targetId = rs.getLong("target_id");
                    Long nullableTargetId = rs.wasNull() ? null : targetId;
                    return new AdminAuditLogRow(rs.getLong("id"), rs.getLong("admin_id"), rs.getString("admin_name"),
                            rs.getString("action"), rs.getString("target_type"), nullableTargetId, rs.getString("detail"),
                            rs.getTimestamp("created_at").toLocalDateTime());
                });
    }

    public long countAuditLogs(Connection connection) throws SQLException {
        Long total = Jdbc.one(connection, "SELECT COUNT(*) total FROM admin_audit_logs", ps -> {
        }, rs -> rs.getLong("total"));
        return total == null ? 0 : total;
    }

    public void logAction(Connection connection, long adminId, String action, String targetType, Long targetId, String detail) throws SQLException {
        Jdbc.update(connection,
                "INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,detail) VALUES(?,?,?,?,?)",
                ps -> {
                    ps.setLong(1, adminId);
                    ps.setString(2, action);
                    ps.setString(3, targetType);
                    if (targetId == null) {
                        ps.setNull(4, Types.BIGINT);
                    } else {
                        ps.setLong(4, targetId);
                    }
                    ps.setString(5, detail);
                });
    }

    private String like(String q) {
        return "%" + (q == null ? "" : q.trim()) + "%";
    }

    private int offset(int page, int size) {
        return (Math.max(1, page) - 1) * Math.max(1, size);
    }
}
