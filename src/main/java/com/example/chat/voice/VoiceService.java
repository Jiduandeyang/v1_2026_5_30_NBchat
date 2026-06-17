package com.example.chat.voice;

import com.example.chat.common.AppException;
import com.example.chat.config.AppConfig;
import com.example.chat.config.Database;
import com.example.chat.model.VoiceCallSession;

import java.sql.Connection;
import java.util.List;

public class VoiceService {
    private final VoiceDao voiceDao = new VoiceDao();
    private final VoiceCallNotifier notifier = new VoiceCallNotifier();

    public long start(long callerId, long calleeId) {
        return start(callerId, calleeId, null);
    }

    public long start(long callerId, long calleeId, VoiceCallStartRequest startRequest) {
        if (callerId == calleeId) {
            throw AppException.badRequest("Cannot call yourself.");
        }
        String callMode = startRequest == null ? "audio" : startRequest.normalizedCallMode();
        try (Connection connection = Database.connection()) {
            voiceDao.cleanupStaleActiveCalls(connection);
            if (voiceDao.isUserInCall(connection, callerId)) {
                throw AppException.badRequest("You are already in a call.");
            }
            if (voiceDao.isUserInCall(connection, calleeId)) {
                throw AppException.badRequest("The user is already in a call.");
            }
            long callId = voiceDao.createCall(connection, callerId, calleeId, callMode);
            VoiceCallSession call = voiceDao.find(connection, callId);
            notifier.notifyCallInvite(callerId, call, startRequest);
            return callId;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public VoiceCallSession incoming(long userId) {
        try (Connection connection = Database.connection()) {
            voiceDao.cleanupStaleActiveCalls(connection);
            return voiceDao.findIncomingRinging(connection, userId);
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public VoiceCallSession findForUser(long userId, long callId) {
        try (Connection connection = Database.connection()) {
            voiceDao.cleanupStaleActiveCalls(connection);
            VoiceCallSession call = voiceDao.find(connection, callId);
            ensureParticipant(userId, call);
            return call;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public VoiceCallSession accept(long userId, long callId) {
        VoiceCallSession call = status(userId, callId, "ACCEPTED");
        notifier.notifyCallAccepted(userId, call);
        return call;
    }

    public VoiceCallSession reject(long userId, long callId) {
        VoiceCallSession call = status(userId, callId, "REJECTED");
        notifier.notifyCallRejected(userId, call);
        return call;
    }

    public VoiceCallSession end(long userId, long callId) {
        VoiceCallSession call = status(userId, callId, "ENDED");
        try (Connection connection = Database.connection()) {
            voiceDao.clearActiveCallsForUserExcept(connection, userId, callId);
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
        notifier.notifyCallEnded(userId, call);
        return call;
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
        try (Connection connection = Database.connection()) {
            VoiceCallSession call = voiceDao.find(connection, callId);
            ensureParticipant(userId, call);
            voiceDao.updateStatus(connection, callId, status);
            return voiceDao.find(connection, callId);
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
