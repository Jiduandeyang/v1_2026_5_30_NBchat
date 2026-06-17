package com.example.chat.voice;

import com.example.chat.common.Jdbc;
import com.example.chat.model.VoiceCallSession;

import java.sql.Connection;
import java.sql.SQLException;

public class VoiceDao {
    public long createCall(Connection connection, long callerId, long calleeId, String callMode) throws SQLException {
        return Jdbc.insert(connection,
                "INSERT INTO voice_call_sessions(caller_id,callee_id,call_mode,status) VALUES(?,?,?,'RINGING')",
                ps -> {
                    ps.setLong(1, callerId);
                    ps.setLong(2, calleeId);
                    ps.setString(3, callMode);
                });
    }

    public boolean isUserInCall(Connection connection, long userId) throws SQLException {
        Long found = Jdbc.one(connection,
                "SELECT 1 FROM voice_call_sessions WHERE (caller_id=? OR callee_id=?) AND status IN ('RINGING','ACCEPTED') LIMIT 1",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                },
                rs -> rs.getLong(1));
        return found != null;
    }

    public void clearActiveCallsForUser(Connection connection, long userId) throws SQLException {
        Jdbc.update(connection,
                "UPDATE voice_call_sessions SET status='MISSED', ended_at=NOW() " +
                        "WHERE (caller_id=? OR callee_id=?) AND status IN ('RINGING','ACCEPTED')",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setLong(2, userId);
                });
    }

    public void clearActiveCallsForUserExcept(Connection connection, long userId, long callId) throws SQLException {
        Jdbc.update(connection,
                "UPDATE voice_call_sessions SET status='MISSED', ended_at=NOW() " +
                        "WHERE id<>? AND (caller_id=? OR callee_id=?) AND status IN ('RINGING','ACCEPTED')",
                ps -> {
                    ps.setLong(1, callId);
                    ps.setLong(2, userId);
                    ps.setLong(3, userId);
                });
    }

    public void cleanupStaleActiveCalls(Connection connection) throws SQLException {
        Jdbc.update(connection,
                "UPDATE voice_call_sessions SET status='MISSED', ended_at=NOW() " +
                        "WHERE status='RINGING' AND started_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)",
                ps -> {
                });
        Jdbc.update(connection,
                "UPDATE voice_call_sessions SET status='MISSED', ended_at=NOW() " +
                        "WHERE status='ACCEPTED' AND accepted_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)",
                ps -> {
                });
    }

    public void updateStatus(Connection connection, long callId, String status) throws SQLException {
        Jdbc.update(connection, "UPDATE voice_call_sessions SET status=?, ended_at=IF(? IN ('ENDED','REJECTED','MISSED','BUSY'), NOW(), ended_at), accepted_at=IF(?='ACCEPTED', NOW(), accepted_at) WHERE id=?",
                ps -> {
                    ps.setString(1, status);
                    ps.setString(2, status);
                    ps.setString(3, status);
                    ps.setLong(4, callId);
                });
    }

    public VoiceCallSession find(Connection connection, long callId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT id,caller_id,callee_id,call_mode,status,started_at,accepted_at,ended_at FROM voice_call_sessions WHERE id=?",
                ps -> ps.setLong(1, callId),
                rs -> new VoiceCallSession(rs.getLong("id"), rs.getLong("caller_id"), rs.getLong("callee_id"),
                        rs.getString("call_mode"),
                        rs.getString("status"),
                        rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toLocalDateTime(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toLocalDateTime()));
    }

    public VoiceCallSession findIncomingRinging(Connection connection, long userId) throws SQLException {
        return Jdbc.one(connection,
                "SELECT id,caller_id,callee_id,call_mode,status,started_at,accepted_at,ended_at " +
                        "FROM voice_call_sessions WHERE callee_id=? AND status='RINGING' ORDER BY id DESC LIMIT 1",
                ps -> ps.setLong(1, userId),
                rs -> new VoiceCallSession(rs.getLong("id"), rs.getLong("caller_id"), rs.getLong("callee_id"),
                        rs.getString("call_mode"),
                        rs.getString("status"),
                        rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toLocalDateTime(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toLocalDateTime()));
    }
}
