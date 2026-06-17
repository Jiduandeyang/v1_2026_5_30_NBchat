package com.example.chat.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAssetContractTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");

    @Test
    void dashboardPageContainsRequiredInteractionTargets() throws IOException {
        String html = read("app.html");

        assertTrue(html.contains("id=\"conversationList\""));
        assertTrue(html.contains("id=\"messageForm\""));
        assertTrue(html.contains("id=\"chatImageInput\""));
        assertTrue(html.contains("id=\"friendGroupForm\""));
        assertTrue(html.contains("id=\"momentImagesInput\""));
        assertTrue(html.contains("id=\"voiceButton\""));
        assertTrue(html.contains("data-dashboard-jump=\"friendsView\""));
        assertTrue(html.contains("data-dashboard-jump=\"groups\""));
        assertTrue(html.contains("data-dashboard-jump=\"chatView\""));
    }

    @Test
    void featureModulesExposeDashboardLoaders() throws IOException {
        assertTrue(read("assets/js/chat.js").contains("window.loadConversations"));
        assertTrue(read("assets/js/friends.js").contains("window.loadFriends"));
        assertTrue(read("assets/js/friends.js").contains("window.loadFriendGroups"));
        assertTrue(read("assets/js/moments.js").contains("window.loadMoments"));
        assertTrue(read("assets/js/profile.js").contains("window.loadProfile"));
    }

    @Test
    void chatModuleUsesNewConversationAndUploadContracts() throws IOException {
        String chat = read("assets/js/chat.js");

        assertTrue(chat.contains("selectedConversation"));
        assertTrue(chat.contains("peerAvatarUrl"));
        assertTrue(chat.contains("CHAT_IMAGE"));
        assertTrue(chat.contains("showAssistantThinking"));
        assertTrue(chat.contains("message.type === \"AI\""));
        assertTrue(chat.contains("/chat/messages"));
        assertTrue(chat.contains("/chat/conversations/${AppState.conversationId}/history"));
    }

    @Test
    void dashboardIncludesMotionAndAiAssistantHooks() throws IOException {
        String html = read("app.html");
        String css = read("assets/css/dashboard.css");

        assertTrue(html.contains("aiHelperHint"));
        assertTrue(html.contains("scene-sun"));
        assertTrue(css.contains("@keyframes workspaceIn"));
        assertTrue(css.contains("@keyframes waterFlow"));
        assertTrue(css.contains("prefers-reduced-motion"));
        assertTrue(css.contains(".message-row.ai"));
    }

    @Test
    void dashboardIncludesSwitchableNightLandscapeHooks() throws IOException {
        String html = read("app.html");
        String css = read("assets/css/dashboard.css");
        String app = read("assets/js/app.js");

        assertTrue(html.contains("id=\"sceneModeToggle\""));
        assertTrue(html.contains("scene-moon"));
        assertTrue(html.contains("scene-stars"));
        assertTrue(css.contains(".nav-illustration.night"));
        assertTrue(css.contains(".app-page.night"));
        assertTrue(css.contains(".app-page.night .feed-card"));
        assertTrue(css.contains("@keyframes starTwinkle"));
        assertTrue(app.contains("localStorage.setItem(\"sceneMode\""));
        assertTrue(app.contains("applySceneMode"));
    }

    @Test
    void momentsAndSidebarExposeImplementedInteractionHooks() throws IOException {
        String app = read("assets/js/app.js");
        String moments = read("assets/js/moments.js");

        assertTrue(moments.contains("uploadMomentImage"));
        assertTrue(moments.contains("renderMomentMedia"));
        assertTrue(moments.contains("setMomentBusy"));
        assertTrue(app.contains("data-dashboard-jump"));
    }

    @Test
    void voiceModuleStartsPrivateCallsWithPeerId() throws IOException {
        String api = read("assets/js/call-api.js");
        String ui = read("assets/js/call-ui.js");

        assertTrue(ui.contains("selectedConversation.peerId"));
        assertTrue(api.contains("/voice/calls/${peerId}"));
        assertTrue(ui.contains("Please select a private chat first."));
    }

    @Test
    void dashboardExposesVoiceDialogAndGroupManagementHooks() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");
        String friends = read("assets/js/friends.js");
        String api = read("assets/js/call-api.js");
        String ui = read("assets/js/call-ui.js");

        assertTrue(html.contains("id=\"voiceDialog\""));
        assertTrue(html.contains("id=\"videoButton\""));
        assertTrue(html.contains("data-lucide=\"video\""));
        assertTrue(html.contains("视频"));
        assertTrue(html.contains("id=\"groupManageButton\""));
        assertTrue(html.contains("id=\"groupManageDialog\""));
        assertTrue(html.contains("id=\"groupFriendPicker\""));
        assertFalse(html.contains("id=\"groupInviteForm\""));
        assertFalse(html.contains("id=\"groupInviteList\""));
        assertTrue(html.contains("data-group-filter"));
        assertTrue(chat.contains("openGroupManageDialog"));
        assertTrue(chat.contains("/chat/groups/${AppState.conversationId}/members"));
        assertTrue(chat.contains("data-set-group-role"));
        assertTrue(chat.contains("data-remove-group-member"));
        assertTrue(chat.contains("currentMemberRole"));
        assertTrue(chat.contains("canManageGroupMembers"));
        assertTrue(chat.contains("canAssignGroupAdmins"));
        assertTrue(friends.contains("data-toggle-close-friend"));
        assertTrue(friends.contains("/chat/group-invitations?mode=received"));
        assertTrue(friends.contains("群聊邀请"));
        assertTrue(friends.contains("group-invite-section"));
        assertTrue(friends.contains("data-accept-group-invite"));
        assertTrue(friends.contains("data-reject-group-invite"));
        assertTrue(chat.contains("window.loadRequests?.()"));
        assertTrue(ui.contains("acceptCall"));
        assertTrue(ui.contains("rejectCall"));
        assertTrue(ui.contains("endCall"));
        assertTrue(api.contains("/voice/ice-servers"));
    }

    @Test
    void dashboardRemovesLegacyGroupInviteAndClutteredSidebarComponents() throws IOException {
        String html = read("app.html");
        String app = read("assets/js/app.js");
        String friends = read("assets/js/friends.js");
        String css = read("assets/css/dashboard.css");

        assertFalse(html.contains("Group Invites"));
        assertFalse(html.contains("id=\"groupInviteForm\""));
        assertFalse(html.contains("id=\"groupInviteList\""));
        assertFalse(html.contains("class=\"quick-grid\""));
        assertFalse(html.contains("id=\"recentFiles\""));
        assertFalse(app.contains("collectRecentFiles"));
        assertFalse(app.contains("createGroupConversation"));
        assertFalse(app.contains("data-recent-file-url"));
        assertFalse(friends.contains("groupInviteForm"));
        assertFalse(friends.contains("groupInviteList"));
        assertTrue(css.contains(".app-page.night .group-friend-row"));
        assertTrue(css.contains(".app-page.night .group-member-row"));
    }

    @Test
    void dashboardIncludesPolishedMotionEffects() throws IOException {
        String html = read("app.html");
        String app = read("assets/js/app.js");
        String chat = read("assets/js/chat.js");
        String moments = read("assets/js/moments.js");
        String css = read("assets/css/dashboard.css");

        assertTrue(html.contains("id=\"chatFxCanvas\""));
        assertTrue(chat.contains("triggerSendParticles"));
        assertTrue(chat.contains("markMentionPulse"));
        assertTrue(chat.contains("wireImageReveal"));
        assertTrue(chat.contains("message-row spring-entry"));
        assertTrue(app.contains("animateThemeSwitch"));
        assertTrue(moments.contains("spawnLikeHearts"));
        assertTrue(css.contains("@keyframes bubbleSpringIn"));
        assertTrue(css.contains("@keyframes mentionPulse"));
        assertTrue(css.contains("@keyframes imageReveal"));
        assertTrue(css.contains("@keyframes heartFloat"));
        assertTrue(css.contains(".theme-transitioning"));
    }

    @Test
    void dashboardIncludesRichMessagingHooks() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");
        String css = read("assets/css/dashboard.css");

        assertTrue(html.contains("id=\"replyPreview\""));
        assertTrue(chat.contains("replyDraft"));
        assertTrue(chat.contains("setReplyDraft"));
        assertTrue(chat.contains("renderReactionBar"));
        assertTrue(chat.contains("sendReaction"));
        assertTrue(chat.contains("startVoiceRecording"));
        assertTrue(chat.contains("VOICE_MESSAGE"));
        assertTrue(chat.contains("voiceMessageFileName(blob.type)"));
        assertTrue(chat.contains("voice-message.wav"));
        assertTrue(chat.contains("audio/wav"));
        assertTrue(chat.contains("encodeWav"));
        assertTrue(chat.contains("voicePcmChunks"));
        assertTrue(chat.contains("voiceMaxAmplitude"));
        assertTrue(chat.contains("data-voice-audio"));
        assertTrue(chat.contains("audio controls preload=\"metadata\""));
        assertFalse(chat.contains("data-voice-play"));
        assertFalse(chat.contains("Number.MAX_SAFE_INTEGER"));
        assertTrue(chat.contains("type: \"VOICE\""));
        assertTrue(chat.contains("message.type === \"VOICE\" || message.type === \"VOICE_MESSAGE\""));
        assertFalse(chat.contains("MediaRecorder"));
        assertTrue(chat.contains("voiceRecordingStartedAt"));
        assertTrue(chat.contains("MIN_VOICE_RECORDING_MS"));
        assertTrue(chat.contains("voiceStopTimer = setTimeout"));
        assertTrue(chat.contains("[\"pointerup\", \"pointercancel\"].forEach"));
        int micStart = chat.indexOf("$(\"#micButton\")?.addEventListener(\"pointerdown\"");
        int emojiStart = chat.indexOf("$(\"#emojiButton\")", micStart);
        assertTrue(micStart >= 0);
        assertTrue(emojiStart > micStart);
        assertFalse(chat.substring(micStart, emojiStart).contains("pointerleave"));
        assertTrue(chat.contains("retryVoiceAudioLoad"));
        assertTrue(chat.contains("data-original-src"));
        assertTrue(chat.contains("data-voice-retry"));
        assertTrue(chat.contains("audio.load()"));
        assertTrue(css.contains(".reply-preview"));
        assertTrue(css.contains(".reaction-bar"));
        assertTrue(css.contains(".composer-recording"));
    }

    @Test
    void apiRetriesTransientGetFailuresAndReportsNetworkErrorsClearly() throws IOException {
        String api = read("assets/js/api.js");

        assertTrue(api.contains("AbortController"));
        assertTrue(api.contains("API_TIMEOUT_MS"));
        assertTrue(api.contains("API_RETRY_DELAY_MS"));
        assertTrue(api.contains("await sleep(API_RETRY_DELAY_MS)"));
        assertTrue(api.contains("method === \"GET\""));
        assertTrue(api.contains("throw new Error(\"网络连接不稳定，请稍后重试\")"));
    }

    @Test
    void dashboardRemovesHeatmapAndKeepsBurnAfterReadingHooks() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");
        String css = read("assets/css/dashboard.css");

        assertFalse(html.contains("id=\"chatHeatmap\""));
        assertFalse(html.contains("id=\"heatmapTotal\""));
        assertFalse(chat.contains("loadChatHeatmap"));
        assertFalse(chat.contains("renderChatHeatmap"));
        assertFalse(chat.contains("/heatmap"));
        assertFalse(css.contains(".heatmap-grid"));
        assertTrue(chat.contains("burnMode"));
        assertTrue(chat.contains("renderBurnMessage"));
        assertTrue(chat.contains("wireBurnCanvases"));
        assertTrue(css.contains(".burn-message"));
    }

    @Test
    void privateProfileCardIsScopedToPrivateChats() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");

        assertTrue(html.contains("id=\"privateProfileCard\""));
        assertTrue(chat.contains("privateProfileCard"));
        assertTrue(chat.contains("renderPrivateProfileCard"));
        assertTrue(chat.contains("card.hidden = !isPrivate"));
    }

    @Test
    void dashboardIncludesRecallMentionAutocompleteSystemAndEmojiPickerHooks() throws IOException {
        String chat = read("assets/js/chat.js");
        String css = read("assets/css/dashboard.css");

        assertTrue(chat.contains("recallMessage"));
        assertTrue(chat.contains("mentionAutocomplete"));
        assertTrue(chat.contains("renderMentionSuggestions"));
        assertTrue(chat.contains("emojiPicker"));
        assertTrue(chat.contains("EMOJI_CHOICES"));
        assertTrue(chat.contains("message.type === \"SYSTEM\""));
        assertTrue(css.contains(".mention-suggestions"));
        assertTrue(css.contains(".emoji-picker"));
        assertTrue(css.contains(".message-row.system"));
        assertTrue(css.contains(".recall-button"));
    }

    @Test
    void chatLayoutPreventsOverflowAndKeepsGroupSettingsScoped() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");
        String css = read("assets/css/dashboard.css");

        assertTrue(css.contains(".chat-panel"));
        assertTrue(css.contains("overflow: hidden"));
        assertTrue(css.contains("overflow-x: hidden"));
        assertTrue(css.contains("overflow-wrap: anywhere"));
        assertTrue(css.contains("max-width: 100%"));
        assertTrue(css.contains("minmax(360px, 1fr)"));
        assertFalse(css.matches("(?s).*\\.message-row\\s*\\{[^}]*width:\\s*fit-content;.*"));
        assertFalse(css.matches("(?s).*\\.message-bubble\\s*\\{[^}]*width:\\s*fit-content;.*"));
        assertTrue(css.matches("(?s).*\\.message-select\\s*\\{[^}]*position:\\s*absolute;.*"));
        assertTrue(chat.contains("ChatApi.put(`/chat/groups/${AppState.conversationId}/settings`"));
        assertTrue(chat.contains("renderGroupSettings(settings)"));
        assertTrue(chat.contains("renderGroupSettings(null)"));
        assertTrue(html.contains("id=\"groupSettingsCard\" hidden"));
        assertTrue(html.indexOf("id=\"messageList\"") == html.lastIndexOf("id=\"messageList\""));
        assertTrue(html.indexOf("选择左侧会话开始聊天") == html.lastIndexOf("选择左侧会话开始聊天"));
    }

    @Test
    void chatEmptyStateKeepsActionsHiddenAndConversationControlsCompact() throws IOException {
        String html = read("app.html");
        String chat = read("assets/js/chat.js");
        String css = read("assets/css/dashboard.css");

        assertTrue(html.contains("<div class=\"chat-actions\" hidden>"));
        assertTrue(html.contains("<form id=\"messageForm\" class=\"composer composer-disabled\">"));
        assertTrue(html.indexOf("id=\"conversationFilters\"") < html.indexOf("id=\"conversationList\""));
        assertTrue(chat.contains("actions.hidden = !hasConversation"));
        assertTrue(chat.contains("composer.classList.toggle(\"composer-disabled\", !hasConversation)"));
        assertTrue(css.contains("[hidden]"));
        assertTrue(css.contains("display: none !important"));
        assertFalse(css.matches("(?s).*\\.conversation-list\\s*\\{[^}]*height:\\s*calc\\(100vh - 250px\\).*"));
        assertTrue(css.matches("(?s).*\\.conversation-panel\\s*\\{[^}]*display:\\s*flex;.*"));
        assertTrue(css.matches("(?s).*\\.conversation-list\\s*\\{[^}]*flex:\\s*1 1 auto;.*"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(WEBAPP.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
