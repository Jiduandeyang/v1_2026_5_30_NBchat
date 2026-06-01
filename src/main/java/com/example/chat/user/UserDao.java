package com.example.chat.user;

import com.example.chat.common.Jdbc;
import com.example.chat.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class UserDao {
    public User findById(Connection connection, long id) throws SQLException {
        return Jdbc.one(connection,
                "SELECT id,username,qq_email,nickname,avatar_url,background_url,signature,role FROM users WHERE id=?",
                ps -> ps.setLong(1, id), this::map);
    }

    public List<User> search(Connection connection, String q) throws SQLException {
        String value = "%" + q + "%";
        return Jdbc.list(connection,
                "SELECT id,username,qq_email,nickname,avatar_url,background_url,signature,role FROM users WHERE username LIKE ? OR qq_email LIKE ? OR nickname LIKE ? LIMIT 30",
                ps -> {
                    ps.setString(1, value);
                    ps.setString(2, value);
                    ps.setString(3, value);
                }, this::map);
    }

    public void updateProfile(Connection connection, long id, ProfileUpdateRequest request) throws SQLException {
        Jdbc.update(connection,
                "UPDATE users SET nickname=?, signature=?, avatar_url=?, background_url=? WHERE id=?",
                ps -> {
                    ps.setString(1, request.nickname());
                    ps.setString(2, request.signature());
                    ps.setString(3, request.avatarUrl());
                    ps.setString(4, request.backgroundUrl());
                    ps.setLong(5, id);
                });
    }

    private User map(java.sql.ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("username"), rs.getString("qq_email"),
                rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("background_url"), rs.getString("signature"), rs.getString("role"), null, false);
    }
}
