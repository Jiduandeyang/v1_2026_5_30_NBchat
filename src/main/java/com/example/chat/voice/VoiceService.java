package com.example.chat.voice;

import com.example.chat.common.AppException;
import com.example.chat.config.AppConfig;
import com.example.chat.config.Database;
import com.example.chat.model.VoiceCallSession;

import java.sql.Connection;
import java.util.List;

public class VoiceService {
    private static final BusyUserRegistry BUSY_USERS = new BusyUserRegistry();
    private final VoiceDao voiceDao = new VoiceDao();

    public long start(long callerId, long calleeId) {
        if (!BUSY_USERS.reserve(callerId, calleeId)) {
            throw AppException.badRequest("User is busy.");
        }
        try (Connection connection = Database.connection()) {
            return voiceDao.createCall(connection, callerId, calleeId);
        } catch (Exception exception) {
            BUSY_USERS.release(callerId, calleeId);
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public VoiceCallSession accept(long userId, long callId) {
        return status(userId, callId, "ACCEPTED");
    }

    public VoiceCallSession reject(long userId, long callId) {
        return status(userId, callId, "REJECTED");
    }

    public VoiceCallSession end(long userId, long callId) {
        return status(userId, callId, "ENDED");
    }

    public List<IceServerConfig.IceServer> iceServers() {
        return IceServerConfig.from(
                AppConfig.get("rtc.stunUrls", "stun:stun.l.google.com:19302"),
                AppConfig.get("rtc.turnUrl", ""),
                AppConfig.get("rtc.turnUsername", ""),
                AppConfig.get("rtc.turnCredential", "")
        );
    }

    public VoiceCallSession status(long userId, long callId, String status) {
        if ("ENDED".equals(status) || "REJECTED".equals(status) || "MISSED".equals(status)) {
            VoiceCallSession call = call(callId);
            ensureParticipant(userId, call);
            BUSY_USERS.release(call.callerId(), call.calleeId());
        }
        try (Connection connection = Database.connection()) {
            voiceDao.updateStatus(connection, callId, status);
            VoiceCallSession updated = voiceDao.find(connection, callId);
            ensureParticipant(userId, updated);
            return updated;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    private VoiceCallSession call(long callId) {
        try (Connection connection = Database.connection()) {
            VoiceCallSession call = voiceDao.find(connection, callId);
            if (call == null) {
                throw AppException.badRequest("Call not found.");
            }
            return call;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    private void ensureParticipant(long userId, VoiceCallSession call) {
        if (call == null || (call.callerId() != userId && call.calleeId() != userId)) {
            throw AppException.badRequest("Call not found.");
        }
    }
}
