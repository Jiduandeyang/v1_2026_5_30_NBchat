package com.example.chat.voice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceCallStateContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void voiceBusyStateUsesDatabaseInsteadOfJvmMemory() throws IOException {
        String service = read("src/main/java/com/example/chat/voice/VoiceService.java");
        String dao = read("src/main/java/com/example/chat/voice/VoiceDao.java");

        assertFalse(Files.exists(ROOT.resolve("src/main/java/com/example/chat/voice/BusyUserRegistry.java")));
        assertFalse(service.contains("BusyUserRegistry"));
        assertFalse(service.contains("BUSY_USERS"));
        assertTrue(service.contains("voiceDao.isUserInCall"));
        assertTrue(dao.contains("isUserInCall"));
        assertTrue(dao.contains("status IN ('RINGING','ACCEPTED')"));
    }

    @Test
    void staleVoiceCallsAreClosedOnStartup() throws IOException {
        String migrator = read("src/main/java/com/example/chat/config/SchemaMigrator.java");
        String database = read("src/main/java/com/example/chat/config/Database.java");

        assertTrue(migrator.contains("cleanupStaleVoiceCalls"));
        assertTrue(migrator.contains("UPDATE voice_call_sessions"));
        assertTrue(migrator.contains("DATE_SUB(NOW(), INTERVAL 5 MINUTE)"));
        assertTrue(database.contains("SchemaMigrator.cleanupStaleVoiceCalls(dataSource)"));
    }

    @Test
    void voiceCleanupAndNavigationShortcutsDoNotLeaveGhostCalls() throws IOException {
        String voiceJs = read("src/main/webapp/assets/js/voice.js");
        String service = read("src/main/java/com/example/chat/voice/VoiceService.java");
        String notifier = read("src/main/java/com/example/chat/voice/VoiceCallNotifier.java");
        String html = read("src/main/webapp/app.html");
        String appJs = read("src/main/webapp/assets/js/app.js");

        assertTrue(voiceJs.contains("function cleanupVoiceCall(closeDialog, notifyServer = true)"));
        assertTrue(voiceJs.contains("ChatApi.post(`/voice/calls/${callId}/end`)"));
        assertTrue(voiceJs.contains("navigator.sendBeacon"));
        assertTrue(voiceJs.contains("keepalive: true"));
        assertTrue(voiceJs.contains("pagehide"));
        assertFalse(html.contains("id=\"voiceNavButton\""));
        assertFalse(html.contains("id=\"exportNavButton\""));
        assertFalse(appJs.contains("voiceNavButton"));
        assertFalse(appJs.contains("exportNavButton"));
        assertTrue(service.contains("notifier.notifyCallEnded"));
        assertTrue(service.contains("notifier.notifyCallRejected"));
        assertTrue(notifier.contains("SocketRegistry.shared()"));
        assertTrue(notifier.contains("\"call-ended\""));
        assertTrue(notifier.contains("\"call-rejected\""));
    }

    @Test
    void voiceClientPlaysRemoteTracksAndRejectsEmptyRecordings() throws IOException {
        String voiceJs = read("src/main/webapp/assets/js/voice.js");
        String chatJs = read("src/main/webapp/assets/js/chat.js");

        assertTrue(voiceJs.contains("playRemoteVoiceAudio"));
        assertTrue(voiceJs.contains("remoteAudio.play()"));
        assertTrue(voiceJs.contains("remoteAudio.muted = false"));
        assertTrue(voiceJs.contains("远端声音已连接"));
        assertTrue(chatJs.contains("blob.size <= 0"));
        assertTrue(chatJs.contains("录音内容为空"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
