package com.example.chat.voice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeCallModuleContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void frontendCallModuleIsSplitByResponsibility() throws IOException {
        assertTrue(Files.exists(ROOT.resolve("src/main/webapp/assets/js/call-api.js")));
        assertTrue(Files.exists(ROOT.resolve("src/main/webapp/assets/js/call-signaling.js")));
        assertTrue(Files.exists(ROOT.resolve("src/main/webapp/assets/js/call-rtc.js")));
        assertTrue(Files.exists(ROOT.resolve("src/main/webapp/assets/js/call-ui.js")));

        String html = read("src/main/webapp/app.html");
        assertTrue(html.contains("assets/js/call-api.js"));
        assertTrue(html.contains("assets/js/call-signaling.js"));
        assertTrue(html.contains("assets/js/call-rtc.js"));
        assertTrue(html.contains("assets/js/call-ui.js"));
        assertTrue(html.indexOf("assets/js/call-api.js") < html.indexOf("assets/js/call-ui.js"));
    }

    @Test
    void callButtonsStartAudioAndVideoThroughOneController() throws IOException {
        String ui = read("src/main/webapp/assets/js/call-ui.js");
        String chat = read("src/main/webapp/assets/js/chat.js");

        assertTrue(ui.contains("window.CallController"));
        assertTrue(ui.contains("startCall(\"audio\")"));
        assertTrue(ui.contains("startCall(\"video\")"));
        assertTrue(ui.contains("acceptCall"));
        assertTrue(ui.contains("rejectCall"));
        assertTrue(ui.contains("endCall"));
        assertTrue(ui.contains("selectedConversation.type !== \"PRIVATE\""));
        assertFalse(chat.contains("conversation?.type !== \"PRIVATE\" || mobileRealtimeUnsupported()"));
    }

    @Test
    void rtcClientHandlesLocalAndRemoteMediaForAudioAndVideo() throws IOException {
        String rtc = read("src/main/webapp/assets/js/call-rtc.js");

        assertTrue(rtc.contains("navigator.mediaDevices.getUserMedia"));
        assertTrue(rtc.contains("mode === \"video\""));
        assertTrue(rtc.contains("audio: true"));
        assertTrue(rtc.contains("video:"));
        assertTrue(rtc.contains("RTCPeerConnection"));
        assertTrue(rtc.contains("addTrack"));
        assertTrue(rtc.contains("ontrack"));
        assertTrue(rtc.contains("remoteVideo.srcObject"));
        assertTrue(rtc.contains("localVideo.srcObject"));
        assertTrue(rtc.contains("remoteAudio.play()"));
        assertTrue(rtc.contains("pendingIceCandidates"));
        assertTrue(rtc.contains("flushPendingIceCandidates"));
    }

    @Test
    void signalingWaitsForSocketAndUsesExplicitSignalTypes() throws IOException {
        String signaling = read("src/main/webapp/assets/js/call-signaling.js");
        String endpoint = read("src/main/java/com/example/chat/websocket/VoiceEndpoint.java");

        assertTrue(signaling.contains("waitForOpen"));
        assertTrue(signaling.contains("call-invite"));
        assertTrue(signaling.contains("call-accepted"));
        assertTrue(signaling.contains("call-rejected"));
        assertTrue(signaling.contains("call-ended"));
        assertTrue(signaling.contains("offer"));
        assertTrue(signaling.contains("answer"));
        assertTrue(signaling.contains("ice"));
        assertTrue(signaling.contains("targetUserId"));
        assertTrue(endpoint.contains("REGISTRY.send(CHANNEL, signal.targetUserId(), json)"));
    }

    @Test
    void backendKeepsCallStateInDatabaseAndDoesNotClearCalleeCallsOnStart() throws IOException {
        String service = read("src/main/java/com/example/chat/voice/VoiceService.java");
        String dao = read("src/main/java/com/example/chat/voice/VoiceDao.java");
        String resource = read("src/main/java/com/example/chat/voice/VoiceResource.java");
        String notifier = read("src/main/java/com/example/chat/voice/VoiceCallNotifier.java");

        assertTrue(service.contains("voiceDao.cleanupStaleActiveCalls(connection)"));
        assertTrue(service.contains("voiceDao.isUserInCall(connection, callerId)"));
        assertTrue(service.contains("voiceDao.isUserInCall(connection, calleeId)"));
        assertFalse(service.contains("clearActiveCallsForUser(connection, calleeId)"));
        assertTrue(dao.contains("call_mode"));
        assertTrue(dao.contains("findIncomingRinging"));
        assertTrue(resource.contains("@Path(\"/calls/incoming\")"));
        assertTrue(resource.contains("@Path(\"/calls/{callId}\")"));
        assertTrue(notifier.contains("\"call-invite\""));
        assertTrue(notifier.contains("\"call-accepted\""));
        assertTrue(notifier.contains("call.callMode()"));
    }

    @Test
    void controllerHasPollingFallbackAndGuaranteedCleanup() throws IOException {
        String ui = read("src/main/webapp/assets/js/call-ui.js");

        assertTrue(ui.contains("pollCallState"));
        assertTrue(ui.contains("/voice/calls/incoming"));
        assertTrue(ui.contains("/voice/calls/${state.callId}"));
        assertTrue(ui.contains("navigator.sendBeacon"));
        assertTrue(ui.contains("CallApi.end"));
        assertTrue(ui.contains("CallRtc.close"));
        assertTrue(ui.contains("CallSignaling.send"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
