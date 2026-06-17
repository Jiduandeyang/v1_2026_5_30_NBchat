package com.example.chat.voice;

import com.example.chat.model.VoiceCallSession;
import com.example.chat.websocket.SocketRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VoiceCallNotifier {
    private static final String CHANNEL = "voice";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final SocketRegistry registry = SocketRegistry.shared();

    public void notifyCallInvite(long fromUserId, VoiceCallSession call, VoiceCallStartRequest startRequest) {
        notifyOtherParticipant(fromUserId, call, "call-invite", new VoiceCallStartRequest(
                startRequest == null ? null : startRequest.conversationId(),
                call == null ? "audio" : call.callMode()
        ));
    }

    public void notifyCallAccepted(long fromUserId, VoiceCallSession call) {
        notifyOtherParticipant(fromUserId, call, "call-accepted", new VoiceCallStartRequest(null,
                call == null ? "audio" : call.callMode()));
    }

    public void notifyCallEnded(long fromUserId, VoiceCallSession call) {
        notifyOtherParticipant(fromUserId, call, "call-ended");
    }

    public void notifyCallRejected(long fromUserId, VoiceCallSession call) {
        notifyOtherParticipant(fromUserId, call, "call-rejected");
    }

    private void notifyOtherParticipant(long fromUserId, VoiceCallSession call, String type) {
        notifyOtherParticipant(fromUserId, call, type, "");
    }

    private void notifyOtherParticipant(long fromUserId, VoiceCallSession call, String type, Object payload) {
        if (call == null) {
            return;
        }
        long targetUserId = call.callerId() == fromUserId ? call.calleeId() : call.callerId();
        try {
            String payloadJson = payload instanceof String text ? text : JSON.writeValueAsString(payload);
            registry.send(CHANNEL, targetUserId, JSON.writeValueAsString(
                    new RoutedVoiceSignal(fromUserId, call.id(), type, payloadJson)));
        } catch (Exception ignored) {
            // Voice REST state changes must succeed even if the peer socket is gone.
        }
    }

    private record RoutedVoiceSignal(long fromUserId, long callId, String type, String payload) {
    }
}
