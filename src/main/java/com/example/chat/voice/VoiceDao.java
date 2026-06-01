package com.example.chat.voice;

import com.example.chat.common.Jdbc;
import com.example.chat.model.VoiceCallSession;

import java.sql.Connection;
import java.sql.SQLException;

public class VoiceDao {
    public long createCall(Connection connection, long callerId, long calleeId) throws SQLException {
        return Jdbc.insert(connection,
                "INSERT INTO voice_call_sessions(caller_id,callee_id,status) VALUES(?,?,'RINGING')",
                ps -> {
                    ps.setLong(1, callerId);
                    ps.setLong(2, calleeId);
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
                "SELECT id,caller_id,callee_id,status,started_at,accepted_at,ended_at FROM voice_call_sessions WHERE id=?",
                ps -> ps.setLong(1, callId),
                rs -> new VoiceCallSession(rs.getLong("id"), rs.getLong("caller_id"), rs.getLong("callee_id"),
                        rs.getString("status"),
                        rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toLocalDateTime(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toLocalDateTime()));
    }
}
