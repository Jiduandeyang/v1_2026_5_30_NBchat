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
        if (callerId == calleeId) {
            throw AppException.badRequest("Cannot call yourself.");
        }
        try (Connection connection = Database.connection()) {
            if (voiceDao.isUserInCall(connection, callerId)) {
                throw AppException.badRequest("你已在通话中。");
            }
            if (voiceDao.isUserInCall(connection, calleeId)) {
                throw AppException.badRequest("对方正在通话中。");
            }
            return voiceDao.createCall(connection, callerId, calleeId);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public VoiceCallSession accept(long userId, long callId) {
        return status(userId, callId, "ACCEPTED");
    }

    public VoiceCallSession reject(long userId, long callId) {
        VoiceCallSession call = status(userId, callId, "REJECTED");
        notifier.notifyCallRejected(userId, call);
        return call;
    }

    public VoiceCallSession end(long userId, long callId) {
        VoiceCallSession call = status(userId, callId, "ENDED");
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
            VoiceCallSession updated = voiceDao.find(connection, callId);
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
