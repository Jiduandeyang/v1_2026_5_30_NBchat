package com.example.chat.chat;

import com.example.chat.ai.AiAssistantService;
import com.example.chat.common.AppException;
import com.example.chat.common.Transactional;
import com.example.chat.config.AppConfig;
import com.example.chat.friend.FriendDao;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.Conversation;
import com.example.chat.model.DailyMessageCount;
import com.example.chat.model.GroupInvitationView;
import com.example.chat.model.GroupMemberView;
import com.example.chat.model.GroupSettingsView;
import com.example.chat.model.MessageReactionSummary;
import com.example.chat.model.ReactionUpdate;
import com.example.chat.model.RecallUpdate;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class ChatService {
    private static final List<String> ALLOWED_GROUP_BACKGROUNDS = List.of("soft-blue", "mint", "neutral", "midnight");
    private final ChatDao chatDao = new ChatDao();
    private final FriendDao friendDao = new FriendDao();
    private final HtmlHistoryExporter exporter = new HtmlHistoryExporter();
    private final AiAssistantService aiAssistantService;

    public ChatService() {
        this(new AiAssistantService());
    }

    ChatService(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    public long privateConversation(long userId, long friendId) {
        return Transactional.withConnection(c -> chatDao.createPrivateConversation(c, userId, friendId));
    }

    public long createGroup(long userId, GroupCreateRequest request) {
        return Transactional.withConnection(c -> {
            long conversationId = chatDao.createGroup(c, userId, request.name());
            Long groupId = chatDao.groupIdForConversation(c, conversationId);
            saveSystemMessage(c, userId, conversationId, "群聊已创建：" + request.name());
            inviteMembers(c, userId, groupId, request.memberIds() == null ? List.of() : request.memberIds());
            return conversationId;
        });
    }

    public void inviteToGroup(long userId, long conversationId, GroupInviteRequest request) {
        Transactional.withConnection(c -> {
            requireManager(c, conversationId, userId);
            Long groupId = chatDao.groupIdForConversation(c, conversationId);
            if (groupId == null) {
                throw AppException.badRequest("Group not found.");
            }
            inviteMembers(c, userId, groupId, request.memberIds() == null ? List.of() : request.memberIds());
            saveSystemMessage(c, userId, conversationId, "群主或管理员发起了新的群聊邀请");
            return null;
        });
    }

    public List<GroupMemberView> groupMembers(long userId, long conversationId) {
        return Transactional.withConnection(c -> {
            requireGroupMember(c, conversationId, userId);
            return chatDao.groupMembers(c, conversationId);
        });
    }

    public GroupSettingsView groupSettings(long userId, long conversationId) {
        return Transactional.withConnection(c -> {
            requireGroupMember(c, conversationId, userId);
            GroupSettingsView settings = chatDao.groupSettings(c, conversationId, userId);
            if (settings == null) {
                throw AppException.badRequest("Group settings not found.");
            }
            return settings;
        });
    }

    public GroupSettingsView updateGroupSettings(long userId, long conversationId, GroupSettingsRequest request) {
        return Transactional.withConnection(c -> {
            requireGroupMember(c, conversationId, userId);
            String remark = normalizeGroupRemark(request == null ? null : request.remark());
            boolean muted = request != null && Boolean.TRUE.equals(request.muted());
            String backgroundKey = normalizeBackgroundKey(request == null ? null : request.backgroundKey());
            String backgroundUrl = normalizeGroupBackgroundUrl(request == null ? null : request.backgroundUrl());
            if (!chatDao.updateGroupSettings(c, conversationId, userId, remark, muted, backgroundKey, backgroundUrl)) {
                throw AppException.badRequest("Group settings not found.");
            }
            return chatDao.groupSettings(c, conversationId, userId);
        });
    }

    public void renameGroup(long userId, long conversationId, String name) {
        returnVoid(c -> {
            requireManager(c, conversationId, userId);
            chatDao.renameGroup(c, conversationId, name);
            saveSystemMessage(c, userId, conversationId, "群聊名称已更新为：" + name);
        });
    }

    public void setGroupMemberRole(long userId, long conversationId, long memberId, String role) {
        returnVoid(c -> {
            requireOwner(c, conversationId, userId);
            String normalizedRole = "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "MEMBER";
            chatDao.setGroupMemberRole(c, conversationId, memberId, normalizedRole);
            saveSystemMessage(c, userId, conversationId, chatDao.displayName(c, memberId) + " 已更新为 " + normalizedRole);
        });
    }

    public void removeGroupMember(long userId, long conversationId, long memberId) {
        returnVoid(c -> {
            requireManager(c, conversationId, userId);
            if (userId == memberId) {
                throw AppException.badRequest("Managers cannot remove themselves here.");
            }
            chatDao.removeGroupMember(c, conversationId, memberId);
            saveSystemMessage(c, userId, conversationId, chatDao.displayName(c, memberId) + " 已被移出群聊");
        });
    }

    public void leaveGroup(long userId, long conversationId) {
        returnVoid(c -> {
            String role = chatDao.memberRole(c, conversationId, userId);
            if (role == null) {
                throw AppException.badRequest("Group not found.");
            }
            if ("OWNER".equals(role)) {
                throw AppException.badRequest("群主需要先转让群主后才能退出群聊。");
            }
            if (!chatDao.leaveGroup(c, conversationId, userId)) {
                throw AppException.badRequest("无法退出群聊。");
            }
            saveSystemMessage(c, userId, conversationId, chatDao.displayName(c, userId) + " 已退出群聊");
        });
    }

    public List<GroupInvitationView> groupInvitations(long userId, String mode) {
        return Transactional.withConnection(c -> chatDao.invitations(c, userId, mode));
    }

    public void acceptGroupInvitation(long userId, long invitationId) {
        Transactional.withConnection(c -> {
            GroupInvitationView invitation = chatDao.invitation(c, invitationId);
            if (invitation == null || invitation.inviteeId() != userId) {
                throw AppException.badRequest("Group invitation not found.");
            }
            chatDao.updateInvitationStatus(c, invitationId, "ACCEPTED");
            chatDao.addGroupMember(c, invitation.groupId(), userId);
            saveSystemMessage(c, userId, invitation.conversationId(), invitation.inviteeName() + " 已加入群聊");
            return null;
        });
    }

    public void rejectGroupInvitation(long userId, long invitationId) {
        Transactional.withConnection(c -> {
            GroupInvitationView invitation = chatDao.invitation(c, invitationId);
            if (invitation == null || invitation.inviteeId() != userId) {
                throw AppException.badRequest("Group invitation not found.");
            }
            chatDao.updateInvitationStatus(c, invitationId, "REJECTED");
            return null;
        });
    }

    public List<Conversation> conversations(long userId) {
        return Transactional.withConnection(c -> chatDao.conversations(c, userId));
    }

    public ChatMessage send(long userId, SendMessageRequest request) {
        return sendWithAssistantReplies(userId, request).get(0);
    }

    public List<ChatMessage> sendWithAssistantReplies(long userId, SendMessageRequest request) {
        ChatMessage userMessage = saveMessage(userId, request);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);

        String conversationType = Transactional.withConnection(c -> chatDao.conversationType(c, request.conversationId()));
        if (shouldTriggerAssistant(conversationType, request.content())) {
            List<ChatMessage> history = history(userId, request.conversationId(), "");
            String reply = aiAssistantService.answer(request.content(), history);
            messages.add(saveAssistantMessage(userId, request.conversationId(), reply));
        }
        return messages;
    }

    boolean shouldTriggerAssistant(String conversationType, String content) {
        return "GROUP".equals(conversationType) && aiAssistantService.isMentioned(content);
    }

    private ChatMessage saveMessage(long userId, SendMessageRequest request) {
        return Transactional.withConnection(c -> {
            ensureCanSend(c, userId, request.conversationId());
            long id = chatDao.saveMessage(c, userId, request);
            ChatMessage message = chatDao.messageById(c, userId, id);
            if (message == null) {
                throw AppException.badRequest("Message not found after save.");
            }
            return message;
        });
    }

    private void ensureCanSend(Connection connection, long userId, long conversationId) throws Exception {
        String conversationType = chatDao.conversationType(connection, conversationId);
        if (conversationType == null || !chatDao.isConversationMember(connection, conversationId, userId)) {
            throw AppException.badRequest("你不在该会话中，无法发送消息。");
        }
        if ("PRIVATE".equals(conversationType)) {
            ensurePrivateFriendshipCanSend(connection, userId, conversationId);
        }
    }

    private void ensurePrivateFriendshipCanSend(Connection connection, long userId, long conversationId) throws Exception {
        Long peerId = chatDao.privatePeerId(connection, conversationId, userId);
        if (peerId == null || !friendDao.isActiveFriend(connection, userId, peerId)) {
            throw AppException.badRequest("对方已不在你的好友列表中，消息未发送。");
        }
    }

    private void saveSystemMessage(Connection connection, long actorId, long conversationId, String content) throws Exception {
        chatDao.saveMessage(connection, actorId, new SendMessageRequest(conversationId, "SYSTEM", content, null, null));
    }

    private ChatMessage saveAssistantMessage(long viewerId, long conversationId, String reply) {
        long assistantUserId = Long.parseLong(AppConfig.get("ai.assistantUserId", "3"));
        return Transactional.withConnection(c -> {
            long id = chatDao.saveMessage(c, assistantUserId, new SendMessageRequest(conversationId, "AI", reply, null, null));
            ChatMessage message = chatDao.messageById(c, viewerId, id);
            if (message == null) {
                throw AppException.badRequest("AI message not found after save.");
            }
            return message;
        });
    }

    public List<ChatMessage> history(long userId, long conversationId, String keyword) {
        return history(userId, conversationId, keyword, ChatHistoryPageRequest.from(null, null));
    }

    public List<ChatMessage> history(long userId, long conversationId, String keyword, ChatHistoryPageRequest page) {
        return Transactional.withConnection(c -> {
            List<ChatMessage> messages = chatDao.history(c, userId, conversationId, keyword, page).stream()
                    .map(message -> withReactions(c, userId, message))
                    .toList();
            chatDao.markConversationRead(c, userId, conversationId);
            return messages;
        });
    }

    public List<MessageReactionSummary> addReaction(long userId, long messageId, String emoji) {
        return addReactionUpdate(userId, messageId, emoji).reactions();
    }

    public List<MessageReactionSummary> removeReaction(long userId, long messageId, String emoji) {
        return removeReactionUpdate(userId, messageId, emoji).reactions();
    }

    public ReactionUpdate addReactionUpdate(long userId, long messageId, String emoji) {
        return Transactional.withConnection(c -> {
            String normalized = normalizeEmoji(emoji);
            chatDao.addReaction(c, userId, messageId, normalized);
            return reactionUpdate(c, userId, messageId);
        });
    }

    public ReactionUpdate removeReactionUpdate(long userId, long messageId, String emoji) {
        return Transactional.withConnection(c -> {
            chatDao.removeReaction(c, userId, messageId, normalizeEmoji(emoji));
            return reactionUpdate(c, userId, messageId);
        });
    }

    private ReactionUpdate reactionUpdate(Connection connection, long userId, long messageId) throws Exception {
        Long conversationId = chatDao.conversationIdForMessage(connection, messageId);
        if (conversationId == null) {
            throw AppException.badRequest("Message not found.");
        }
        return ReactionUpdate.of(conversationId, messageId, chatDao.reactions(connection, userId, messageId));
    }

    private ChatMessage withReactions(Connection connection, long userId, ChatMessage message) {
        try {
            return new ChatMessage(message.id(), message.conversationId(), message.senderId(), message.senderName(),
                    message.type(), message.content(), message.mediaId(), message.mediaUrl(), message.replyToMessageId(),
                    message.replySenderName(), message.replyPreview(), chatDao.reactions(connection, userId, message.id()),
                    message.recalledAt(), message.sentAt());
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    private String normalizeEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw AppException.badRequest("Reaction is required.");
        }
        String normalized = emoji.trim();
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    private String normalizeGroupRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        String normalized = remark.trim();
        if (normalized.length() > 80) {
            throw AppException.badRequest("Group remark must be 80 characters or fewer.");
        }
        return normalized;
    }

    private String normalizeBackgroundKey(String backgroundKey) {
        if (backgroundKey == null || backgroundKey.isBlank()) {
            return "soft-blue";
        }
        String normalized = backgroundKey.trim();
        if (!ALLOWED_GROUP_BACKGROUNDS.contains(normalized)) {
            throw AppException.badRequest("Unsupported group background.");
        }
        return normalized;
    }

    private String normalizeGroupBackgroundUrl(String backgroundUrl) {
        if (backgroundUrl == null || backgroundUrl.isBlank()) {
            return null;
        }
        String normalized = backgroundUrl.trim();
        if (normalized.length() > 255) {
            throw AppException.badRequest("Group background URL must be 255 characters or fewer.");
        }
        return normalized;
    }

    public String exportHtml(long userId, long conversationId) {
        List<ChatMessage> messages = history(userId, conversationId, "");
        return exporter.export("Conversation " + conversationId, messages.stream()
                .map(m -> new HtmlHistoryExporter.ExportMessage(m.senderName(), m.type(), m.content(), m.sentAt()))
                .toList());
    }

    public List<Long> recipients(long conversationId, long senderId) {
        return Transactional.withConnection(c -> chatDao.recipients(c, conversationId, senderId));
    }

    public RecallUpdate recallMessage(long userId, long messageId) {
        return Transactional.withConnection(c -> {
            Long conversationId = chatDao.conversationIdForMessage(c, messageId);
            if (conversationId == null) {
                throw AppException.badRequest("Message not found.");
            }
            if (!chatDao.recallMessage(c, userId, messageId)) {
                throw AppException.badRequest("Only your own messages can be recalled within 2 minutes.");
            }
            ChatMessage message = chatDao.messageById(c, userId, messageId);
            if (message == null) {
                throw AppException.badRequest("Message not found after recall.");
            }
            return RecallUpdate.of(withReactions(c, userId, message));
        });
    }

    public List<String> messageReadBy(long userId, long messageId) {
        return Transactional.withConnection(c -> chatDao.messageReadBy(c, messageId, userId));
    }

    public List<String> recentTexts(long userId, long conversationId) {
        return Transactional.withConnection(c -> chatDao.recentTexts(c, userId, conversationId));
    }

    public void hideMessages(long userId, List<Long> messageIds) {
        Transactional.run(c -> {
            for (Long mid : messageIds) {
                chatDao.hideMessageForUser(c, userId, mid);
            }
        });
    }

    public void clearHistory(long userId, long conversationId) {
        returnVoid(c -> chatDao.clearConversationForUser(c, conversationId, userId));
    }

    public String getGroupAnnouncement(long userId, long conversationId) {
        return Transactional.withConnection(c -> {
            requireGroupMember(c, conversationId, userId);
            return chatDao.getGroupAnnouncement(c, conversationId);
        });
    }

    public void updateGroupAnnouncement(long userId, long conversationId, String announcement) {
        returnVoid(c -> {
            requireManager(c, conversationId, userId);
            chatDao.updateGroupAnnouncement(c, conversationId,
                    announcement == null || announcement.isBlank() ? null : announcement.trim());
            saveSystemMessage(c, userId, conversationId, "群公告已更新");
        });
    }

    public List<DailyMessageCount> heatmap(long userId, long conversationId) {
        return Transactional.withConnection(c -> chatDao.dailyMessageCounts(c, userId, conversationId));
    }

    public void markBurnMessageRead(long userId, long messageId) {
        returnVoid(c -> {
            if (!chatDao.hideBurnMessageForUser(c, userId, messageId)) {
                throw AppException.badRequest("Burn-after-reading message not found.");
            }
        });
    }

    private void inviteMembers(Connection connection, long inviterId, long groupId, List<Long> memberIds) throws Exception {
        for (Long memberId : memberIds.stream().distinct().toList()) {
            if (memberId == null || memberId == inviterId) {
                continue;
            }
            boolean invitedMarkedInviterClose = friendDao.isCloseFriend(connection, memberId, inviterId);
            if (GroupInvitePolicy.decide(invitedMarkedInviterClose) == GroupInviteDecision.ADD_DIRECTLY) {
                chatDao.addGroupMember(connection, groupId, memberId);
            } else {
                chatDao.createGroupInvitation(connection, groupId, inviterId, memberId);
            }
        }
    }

    private void returnVoid(Transactional.SqlConsumer work) {
        Transactional.withConnection(c -> {
            work.run(c);
            return null;
        });
    }

    private void requireGroupMember(Connection connection, long conversationId, long userId) throws Exception {
        String role = chatDao.memberRole(connection, conversationId, userId);
        if (role == null) {
            throw AppException.badRequest("Group not found.");
        }
    }

    private void requireManager(Connection connection, long conversationId, long userId) throws Exception {
        String role = chatDao.memberRole(connection, conversationId, userId);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw AppException.badRequest("Only group owners or admins can manage this group.");
        }
    }

    private void requireOwner(Connection connection, long conversationId, long userId) throws Exception {
        String role = chatDao.memberRole(connection, conversationId, userId);
        if (!"OWNER".equals(role)) {
            throw AppException.badRequest("Only the group owner can change admin roles.");
        }
    }
}
