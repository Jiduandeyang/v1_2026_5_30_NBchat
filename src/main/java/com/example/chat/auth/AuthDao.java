package com.example.chat.auth;

import com.example.chat.common.Jdbc;
import com.example.chat.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class AuthDao {
    public User findByUsername(Connection connection, String username) throws SQLException {
        return Jdbc.one(connection,
                "SELECT id,username,qq_email,nickname,avatar_url,background_url,signature,role,disabled FROM users WHERE username=?",
                ps -> ps.setString(1, username),
                rs -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("qq_email"),
                        rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("background_url"),
                        rs.getString("signature"), rs.getString("role"), rs.getBoolean("disabled"), null, false));
    }

    public String passwordHashByUsername(Connection connection, String username) throws SQLException {
        return Jdbc.one(connection, "SELECT password_hash FROM users WHERE username=?", ps -> ps.setString(1, username),
                rs -> rs.getString("password_hash"));
    }

    public boolean usernameExists(Connection connection, String username) throws SQLException {
        Long found = Jdbc.one(connection, "SELECT 1 FROM users WHERE username=?",
                ps -> ps.setString(1, username),
                rs -> rs.getLong(1));
        return found != null;
    }

    public boolean emailExists(Connection connection, String qqEmail) throws SQLException {
        Long found = Jdbc.one(connection, "SELECT 1 FROM users WHERE qq_email=?",
                ps -> ps.setString(1, qqEmail),
                rs -> rs.getLong(1));
        return found != null;
    }

    public long createUser(Connection connection, RegisterRequest request, String passwordHash) throws SQLException {
        long id = Jdbc.insert(connection,
                "INSERT INTO users(username,password_hash,qq_email,nickname) VALUES(?,?,?,?)",
                ps -> {
                    ps.setString(1, request.username());
                    ps.setString(2, passwordHash);
                    ps.setString(3, request.qqEmail());
                    ps.setString(4, request.nickname() == null || request.nickname().isBlank() ? request.username() : request.nickname());
                });
        Jdbc.insert(connection, "INSERT INTO friend_groups(owner_id,name) VALUES(?,?)", ps -> {
            ps.setLong(1, id);
            ps.setString(2, "My Friends");
        });
        long convId = Jdbc.insert(connection, "INSERT INTO conversations(type,title) VALUES('PRIVATE',?)",
                ps -> ps.setString(1, "系统消息"));
        Jdbc.update(connection, "INSERT INTO conversation_members(conversation_id,user_id) VALUES(?,?)", ps -> {
            ps.setLong(1, convId); ps.setLong(2, id); });
        Jdbc.update(connection, "INSERT INTO messages(conversation_id,sender_id,type,content) VALUES(?,?,'SYSTEM',?)", ps -> {
            ps.setLong(1, convId); ps.setLong(2, id);
            ps.setString(3, "👋 欢迎来到 NBchat！这是一个 Jakarta EE 全栈聊天系统。\n\n🚀 快速开始：\n1. 点击左侧「联系人」→ 搜索 alice 或 bob 加好友\n2. 在群聊里输入 @千问小助手 问任何问题\n3. 点击聊天区右上角 + 创建你的第一个群聊\n4. 试试发一条图片、语音或阅后即焚消息\n\n💡 小提示：右键消息可以快速引用回复。祝你聊天愉快！");
        });
        return id;
    }

    public void saveEmailCode(Connection connection, String qqEmail, String code, String purpose) throws SQLException {
        Jdbc.update(connection,
                "INSERT INTO email_verification_codes(qq_email,code,purpose,expires_at) VALUES(?,?,?,?)",
                ps -> {
                    ps.setString(1, qqEmail);
                    ps.setString(2, code);
                    ps.setString(3, purpose);
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now().plusMinutes(10)));
                });
    }

    public void markExistingCodesUsed(Connection connection, String qqEmail, String purpose) throws SQLException {
        Jdbc.update(connection,
                "UPDATE email_verification_codes SET used=1 WHERE qq_email=? AND purpose=? AND used=0",
                ps -> {
                    ps.setString(1, qqEmail);
                    ps.setString(2, purpose);
                });
    }

    public void markPreviousEmailCodesUsed(Connection connection, String qqEmail, String purpose) throws SQLException {
        markExistingCodesUsed(connection, qqEmail, purpose);
    }

    public LocalDateTime latestEmailCodeCreatedAt(Connection connection, String qqEmail, String purpose) throws SQLException {
        Timestamp createdAt = Jdbc.one(connection,
                "SELECT created_at FROM email_verification_codes WHERE qq_email=? AND purpose=? ORDER BY id DESC LIMIT 1",
                ps -> {
                    ps.setString(1, qqEmail);
                    ps.setString(2, purpose);
                },
                rs -> rs.getTimestamp("created_at"));
        return createdAt == null ? null : createdAt.toLocalDateTime();
    }

    public boolean consumeEmailCode(Connection connection, String qqEmail, String code, String purpose) throws SQLException {
        int count = Jdbc.update(connection,
                "UPDATE email_verification_codes SET used=1 WHERE qq_email=? AND code=? AND purpose=? AND used=0 AND expires_at>NOW() " +
                        "AND id=(SELECT latestCode.id FROM (SELECT MAX(id) id FROM email_verification_codes WHERE qq_email=? AND purpose=?) latestCode)",
                ps -> {
                    ps.setString(1, qqEmail);
                    ps.setString(2, code);
                    ps.setString(3, purpose);
                    ps.setString(4, qqEmail);
                    ps.setString(5, purpose);
                });
        return count > 0;
    }

    public void updatePassword(Connection connection, String qqEmail, String passwordHash) throws SQLException {
        Jdbc.update(connection, "UPDATE users SET password_hash=? WHERE qq_email=?", ps -> {
            ps.setString(1, passwordHash);
            ps.setString(2, qqEmail);
        });
    }
}
