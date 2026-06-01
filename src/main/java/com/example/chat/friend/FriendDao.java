package com.example.chat.friend;

import com.example.chat.common.Jdbc;
import com.example.chat.model.FriendGroup;
import com.example.chat.model.FriendRequestView;
import com.example.chat.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class FriendDao {
    public List<FriendGroup> groups(Connection connection, long ownerId) throws SQLException {
        return Jdbc.list(connection, "SELECT id,owner_id,name FROM friend_groups WHERE owner_id=? ORDER BY id",
                ps -> ps.setLong(1, ownerId),
                rs -> new FriendGroup(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name")));
    }

    public long createGroup(Connection connection, long ownerId, String name) throws SQLException {
        return Jdbc.insert(connection, "INSERT INTO friend_groups(owner_id,name) VALUES(?,?)", ps -> {
            ps.setLong(1, ownerId);
            ps.setString(2, name);
        });
    }

    public void renameGroup(Connection connection, long ownerId, long groupId, String name) throws SQLException {
        Jdbc.update(connection, "UPDATE friend_groups SET name=? WHERE id=? AND owner_id=?", ps -> {
            ps.setString(1, name);
            ps.setLong(2, groupId);
            ps.setLong(3, ownerId);
        });
    }

    public void moveFriend(Connection connection, long ownerId, long friendId, long groupId) throws SQLException {
        Jdbc.update(connection, "UPDATE friendships SET group_id=? WHERE owner_id=? AND friend_id=?", ps -> {
            ps.setLong(1, groupId);
            ps.setLong(2, ownerId);
            ps.setLong(3, friendId);
        });
    }

    public void sendRequest(Connection connection, long senderId, long receiverId, String message) throws SQLException {
        Jdbc.update(connection,
                "INSERT INTO friend_requests(sender_id,receiver_id,message,status) VALUES(?,?,?,'PENDING')",
                ps -> {
                    ps.setLong(1, senderId);
                    ps.setLong(2, receiverId);
                    ps.setString(3, message);
                });
    }

    public void updateRequest(Connection connection, long requestId, long receiverId, String status) throws SQLException {
        Jdbc.update(connection, "UPDATE friend_requests SET status=? WHERE id=? AND receiver_id=?", ps -> {
            ps.setString(1, status);
            ps.setLong(2, requestId);
            ps.setLong(3, receiverId);
        });
    }

    public FriendRequestView request(Connection connection, long requestId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT fr.*, su.nickname sender_name, ru.nickname receiver_name FROM friend_requests fr JOIN users su ON su.id=fr.sender_id JOIN users ru ON ru.id=fr.receiver_id WHERE fr.id=?",
                ps -> ps.setLong(1, requestId),
                rs -> new FriendRequestView(rs.getLong("id"), rs.getLong("sender_id"), rs.getLong("receiver_id"),
                        rs.getString("sender_name"), rs.getString("receiver_name"), rs.getString("message"),
                        rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public List<FriendRequestView> requests(Connection connection, long userId, String mode) throws SQLException {
        String column = "sent".equals(mode) ? "sender_id" : "receiver_id";
        return Jdbc.list(connection,
                "SELECT fr.*, su.nickname sender_name, ru.nickname receiver_name FROM friend_requests fr JOIN users su ON su.id=fr.sender_id JOIN users ru ON ru.id=fr.receiver_id WHERE fr." + column + "=? ORDER BY fr.id DESC",
                ps -> ps.setLong(1, userId),
                rs -> new FriendRequestView(rs.getLong("id"), rs.getLong("sender_id"), rs.getLong("receiver_id"),
                        rs.getString("sender_name"), rs.getString("receiver_name"), rs.getString("message"),
                        rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public List<User> friends(Connection connection, long ownerId) throws SQLException {
        return Jdbc.list(connection,
                "SELECT u.id,u.username,u.qq_email,u.nickname,u.avatar_url,u.background_url,u.signature,u.role,f.group_id,f.close_friend FROM friendships f JOIN users u ON u.id=f.friend_id WHERE f.owner_id=? AND f.active=1 ORDER BY u.nickname",
                ps -> ps.setLong(1, ownerId),
                rs -> {
                    long groupIdValue = rs.getLong("group_id");
                    Long groupId = rs.wasNull() ? null : groupIdValue;
                    return new User(rs.getLong("id"), rs.getString("username"), rs.getString("qq_email"),
                            rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("background_url"), rs.getString("signature"), rs.getString("role"), groupId, rs.getBoolean("close_friend"));
                });
    }

    public boolean isCloseFriend(Connection connection, long ownerId, long friendId) throws SQLException {
        Boolean value = Jdbc.one(connection,
                "SELECT close_friend FROM friendships WHERE owner_id=? AND friend_id=? AND active=1",
                ps -> {
                    ps.setLong(1, ownerId);
                    ps.setLong(2, friendId);
                },
                rs -> rs.getBoolean("close_friend"));
        return Boolean.TRUE.equals(value);
    }

    public boolean isActiveFriend(Connection connection, long ownerId, long friendId) throws SQLException {
        Long value = Jdbc.one(connection,
                "SELECT 1 FROM friendships WHERE owner_id=? AND friend_id=? AND active=1",
                ps -> {
                    ps.setLong(1, ownerId);
                    ps.setLong(2, friendId);
                },
                rs -> rs.getLong(1));
        return value != null;
    }

    public void setCloseFriend(Connection connection, long ownerId, long friendId, boolean closeFriend) throws SQLException {
        Jdbc.update(connection, "UPDATE friendships SET close_friend=? WHERE owner_id=? AND friend_id=? AND active=1", ps -> {
            ps.setBoolean(1, closeFriend);
            ps.setLong(2, ownerId);
            ps.setLong(3, friendId);
        });
    }

    public void createFriendshipPair(Connection connection, long a, long b) throws SQLException {
        Jdbc.update(connection, "INSERT INTO friendships(owner_id,friend_id,active) VALUES(?,?,1) ON DUPLICATE KEY UPDATE active=1", ps -> {
            ps.setLong(1, a);
            ps.setLong(2, b);
        });
        Jdbc.update(connection, "INSERT INTO friendships(owner_id,friend_id,active) VALUES(?,?,1) ON DUPLICATE KEY UPDATE active=1", ps -> {
            ps.setLong(1, b);
            ps.setLong(2, a);
        });
    }

    public void deleteForOwnerOnly(Connection connection, long ownerId, long friendId) throws SQLException {
        Jdbc.update(connection, "UPDATE friendships SET active=0 WHERE owner_id=? AND friend_id=?", ps -> {
            ps.setLong(1, ownerId);
            ps.setLong(2, friendId);
        });
        Jdbc.update(connection,
                "INSERT INTO message_visibility(message_id,user_id,hidden) " +
                        "SELECT m.id, ?, 1 FROM messages m JOIN conversation_members cm1 ON cm1.conversation_id=m.conversation_id AND cm1.user_id=? " +
                        "JOIN conversation_members cm2 ON cm2.conversation_id=m.conversation_id AND cm2.user_id=? " +
                        "JOIN conversations c ON c.id=m.conversation_id AND c.type='PRIVATE' " +
                        "ON DUPLICATE KEY UPDATE hidden=1",
                ps -> {
                    ps.setLong(1, ownerId);
                    ps.setLong(2, ownerId);
                    ps.setLong(3, friendId);
                });
    }
}
