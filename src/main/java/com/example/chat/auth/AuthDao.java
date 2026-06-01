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
                "SELECT id,username,qq_email,nickname,avatar_url,background_url,signature,role FROM users WHERE username=?",
                ps -> ps.setString(1, username),
                rs -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("qq_email"),
                        rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("background_url"), rs.getString("signature"), rs.getString("role"), null, false));
    }

    public String passwordHashByUsername(Connection connection, String username) throws SQLException {
        return Jdbc.one(connection, "SELECT password_hash FROM users WHERE username=?", ps -> ps.setString(1, username),
                rs -> rs.getString("password_hash"));
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

    public boolean consumeEmailCode(Connection connection, String qqEmail, String code, String purpose) throws SQLException {
        int count = Jdbc.update(connection,
                "UPDATE email_verification_codes SET used=1 WHERE qq_email=? AND code=? AND purpose=? AND used=0 AND expires_at>NOW()",
                ps -> {
                    ps.setString(1, qqEmail);
                    ps.setString(2, code);
                    ps.setString(3, purpose);
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
