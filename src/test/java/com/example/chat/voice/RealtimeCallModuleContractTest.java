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
        assertTrue(rtc.contains("audio:"));
        assertTrue(rtc.contains("video:"));
        assertTrue(rtc.contains("RTCPeerConnection"));
        assertTrue(rtc.contains("addTrack"));
        assertTrue(rtc.contains("replaceTrack(track)"));
        assertTrue(rtc.contains("ontrack"));
        assertTrue(rtc.contains("remoteVideo.srcObject"));
        assertTrue(rtc.contains("localVideo.srcObject"));
        assertTrue(rtc.contains("playMedia(remoteAudio)"));
        assertTrue(rtc.contains("pendingIceCandidates"));
        assertTrue(rtc.contains("flushPendingIceCandidates"));
    }

    @Test
    void rtcPrepareKeepsIceSignalSenderAfterCleanup() throws IOException {
        String rtc = read("src/main/webapp/assets/js/call-rtc.js");
        int prepareStart = rtc.indexOf("async function prepare");
        int cleanup = rtc.indexOf("close(true);", prepareStart);
        int senderAssignment = rtc.indexOf("signalSender = onSignal;", prepareStart);

        assertTrue(prepareStart >= 0);
        assertTrue(cleanup > prepareStart);
        assertTrue(senderAssignment > cleanup);
    }

    @Test
    void rtcSplitsRemoteAudioAndVideoPlaybackStreams() throws IOException {
        String rtc = read("src/main/webapp/assets/js/call-rtc.js");

        assertTrue(rtc.contains("remoteAudioStream"));
        assertTrue(rtc.contains("remoteVideoStream"));
        assertTrue(rtc.contains("addTrackOnce(remoteAudioStream"));
        assertTrue(rtc.contains("addTrackOnce(remoteVideoStream"));
        assertTrue(rtc.contains("remoteVideo.muted = true"));
    }

    @Test
    void rtcAllowsDirectAndRelayCandidates() throws IOException {
        String rtc = read("src/main/webapp/assets/js/call-rtc.js");

        assertTrue(rtc.contains("iceTransportPolicy: \"all\""));
    }

    @Test
    void rtcReportsRemoteMediaOnlyAfterRemoteTrackCanPlay() throws IOException {
        String rtc = read("src/main/webapp/assets/js/call-rtc.js");

        assertTrue(rtc.contains("onRemoteMedia"));
        assertTrue(rtc.contains("reportRemoteMedia(\"audio\")"));
        assertTrue(rtc.contains("reportRemoteMedia(\"video\")"));
        assertTrue(rtc.contains("track.onunmute"));
    }

    @Test
    void controllerDoesNotShowConnectedUntilRequiredRemoteMediaArrives() throws IOException {
        String ui = read("src/main/webapp/assets/js/call-ui.js");

        assertTrue(ui.contains("remoteAudioReady"));
        assertTrue(ui.contains("remoteVideoReady"));
        assertTrue(ui.contains("function hasRequiredRemoteMedia"));
        assertTrue(ui.contains("function markRemoteMedia"));

        int remoteMediaStart = ui.indexOf("onRemoteMedia");
        int stateChangeStart = ui.indexOf("onStateChange");
        int prepareEnd = ui.indexOf("});", stateChangeStart);
        String stateChangeBlock = ui.substring(stateChangeStart, prepareEnd);

        assertTrue(remoteMediaStart >= 0);
        assertTrue(ui.substring(remoteMediaStart, stateChangeStart).contains("\"Connected.\""));
        assertFalse(stateChangeBlock.contains("\"Connected.\""));
    }

    @Test
    void controllerAvoidsDuplicateOfferCreationAfterAcceptNotifications() throws IOException {
        String ui = read("src/main/webapp/assets/js/call-ui.js");
        int sendOfferStart = ui.indexOf("async function sendOfferOnce");
        int guard = ui.indexOf("state.offerSent = true;", sendOfferStart);
        int createOffer = ui.indexOf("CallRtc.createOffer()", sendOfferStart);
        int acceptStart = ui.indexOf("async function acceptCall");
        int acceptEnd = ui.indexOf("async function rejectCall", acceptStart);
        String acceptBody = ui.substring(acceptStart, acceptEnd);

        assertTrue(sendOfferStart >= 0);
        assertTrue(guard > sendOfferStart);
        assertTrue(createOffer > guard);
        assertFalse(acceptBody.contains("\"call-accepted\""));
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
