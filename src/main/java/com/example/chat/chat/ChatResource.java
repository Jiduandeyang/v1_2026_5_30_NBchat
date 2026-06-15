package com.example.chat.chat;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.Conversation;
import com.example.chat.model.DailyMessageCount;
import com.example.chat.model.GroupMemberView;
import com.example.chat.model.GroupInvitationView;
import com.example.chat.model.GroupSettingsView;
import com.example.chat.model.MessageReactionSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Path("/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {
    private final ChatService chatService = new ChatService();

    @POST
    @Path("/private")
    public ApiResponse<Long> privateConversation(PrivateConversationRequest privateRequest, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.privateConversation(SessionSupport.requireUserId(request), privateRequest.friendId()));
    }

    @POST
    @Path("/groups")
    public ApiResponse<Long> createGroup(GroupCreateRequest group, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.createGroup(SessionSupport.requireUserId(request), group));
    }

    @POST
    @Path("/groups/{conversationId}/invitations")
    public ApiResponse<Void> inviteGroupMembers(@PathParam("conversationId") long conversationId, GroupInviteRequest invite, @Context HttpServletRequest request) {
        chatService.inviteToGroup(SessionSupport.requireUserId(request), conversationId, invite);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/groups/{conversationId}/members")
    public ApiResponse<List<GroupMemberView>> groupMembers(@PathParam("conversationId") long conversationId, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.groupMembers(SessionSupport.requireUserId(request), conversationId));
    }

    @GET
    @Path("/groups/{conversationId}/settings")
    public ApiResponse<GroupSettingsView> groupSettings(@PathParam("conversationId") long conversationId, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.groupSettings(SessionSupport.requireUserId(request), conversationId));
    }

    @PUT
    @Path("/groups/{conversationId}/settings")
    public ApiResponse<GroupSettingsView> updateGroupSettings(
            @PathParam("conversationId") long conversationId,
            GroupSettingsRequest settings,
            @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.updateGroupSettings(SessionSupport.requireUserId(request), conversationId, settings));
    }

    @POST
    @Path("/groups/{conversationId}/members")
    public ApiResponse<Void> addGroupMembers(@PathParam("conversationId") long conversationId, GroupInviteRequest invite, @Context HttpServletRequest request) {
        chatService.inviteToGroup(SessionSupport.requireUserId(request), conversationId, invite);
        return ApiResponse.ok(null);
    }

    @PUT
    @Path("/groups/{conversationId}/name")
    public ApiResponse<Void> renameGroup(@PathParam("conversationId") long conversationId, GroupNameRequest groupName, @Context HttpServletRequest request) {
        chatService.renameGroup(SessionSupport.requireUserId(request), conversationId, groupName.name());
        return ApiResponse.ok(null);
    }

    @PUT
    @Path("/groups/{conversationId}/members/{memberId}/role")
    public ApiResponse<Void> setGroupMemberRole(
            @PathParam("conversationId") long conversationId,
            @PathParam("memberId") long memberId,
            GroupRoleRequest role,
            @Context HttpServletRequest request) {
        chatService.setGroupMemberRole(SessionSupport.requireUserId(request), conversationId, memberId, role.role());
        return ApiResponse.ok(null);
    }

    @DELETE
    @Path("/groups/{conversationId}/members/{memberId}")
    public ApiResponse<Void> removeGroupMember(
            @PathParam("conversationId") long conversationId,
            @PathParam("memberId") long memberId,
            @Context HttpServletRequest request) {
        chatService.removeGroupMember(SessionSupport.requireUserId(request), conversationId, memberId);
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/groups/{conversationId}/leave")
    public ApiResponse<Void> leaveGroup(@PathParam("conversationId") long conversationId, @Context HttpServletRequest request) {
        chatService.leaveGroup(SessionSupport.requireUserId(request), conversationId);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/group-invitations")
    public ApiResponse<List<GroupInvitationView>> groupInvitations(@QueryParam("mode") @DefaultValue("received") String mode, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.groupInvitations(SessionSupport.requireUserId(request), mode));
    }

    @POST
    @Path("/group-invitations/{id}/accept")
    public ApiResponse<Void> acceptGroupInvitation(@PathParam("id") long id, @Context HttpServletRequest request) {
        chatService.acceptGroupInvitation(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/group-invitations/{id}/reject")
    public ApiResponse<Void> rejectGroupInvitation(@PathParam("id") long id, @Context HttpServletRequest request) {
        chatService.rejectGroupInvitation(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/conversations")
    public ApiResponse<List<Conversation>> conversations(@Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.conversations(SessionSupport.requireUserId(request)));
    }

    @POST
    @Path("/messages")
    public ApiResponse<ChatMessage> send(SendMessageRequest message, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.send(SessionSupport.requireUserId(request), message));
    }

    @POST
    @Path("/messages/{messageId}/reactions")
    public ApiResponse<List<MessageReactionSummary>> addReaction(
            @PathParam("messageId") long messageId,
            MessageReactionRequest reaction,
            @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.addReaction(SessionSupport.requireUserId(request), messageId, reaction.emoji()));
    }

    @POST
    @Path("/messages/{messageId}/recall")
    public ApiResponse<ChatMessage> recallMessage(@PathParam("messageId") long messageId, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.recallMessage(SessionSupport.requireUserId(request), messageId).message());
    }

    @POST
    @Path("/messages/{messageId}/burn-read")
    public ApiResponse<Void> markBurnMessageRead(@PathParam("messageId") long messageId, @Context HttpServletRequest request) {
        chatService.markBurnMessageRead(SessionSupport.requireUserId(request), messageId);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/messages/{messageId}/read-by")
    public ApiResponse<List<String>> messageReadBy(@PathParam("messageId") long messageId, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.messageReadBy(SessionSupport.requireUserId(request), messageId));
    }

    @DELETE
    @Path("/messages/{messageId}/reactions/{emoji}")
    public ApiResponse<List<MessageReactionSummary>> removeReaction(
            @PathParam("messageId") long messageId,
            @PathParam("emoji") String emoji,
            @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.removeReaction(SessionSupport.requireUserId(request), messageId, emoji));
    }

    @GET
    @Path("/conversations/{id}/history")
    public ApiResponse<List<ChatMessage>> history(
            @PathParam("id") long id,
            @QueryParam("q") String q,
            @QueryParam("limit") Integer limit,
            @QueryParam("beforeId") Long beforeId,
            @Context HttpServletRequest request) {
        ChatHistoryPageRequest page = ChatHistoryPageRequest.from(limit, beforeId);
        return ApiResponse.ok(chatService.history(SessionSupport.requireUserId(request), id, q, page));
    }

    @POST
    @Path("/conversations/{id}/clear")
    public ApiResponse<Void> clearHistory(@PathParam("id") long id, @Context HttpServletRequest request) {
        chatService.clearHistory(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/messages/hide")
    public ApiResponse<Void> hideMessages(List<Long> messageIds, @Context HttpServletRequest request) {
        chatService.hideMessages(SessionSupport.requireUserId(request), messageIds);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/conversations/{id}/wordcloud")
    public ApiResponse<List<String>> wordcloud(@PathParam("id") long id, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.recentTexts(SessionSupport.requireUserId(request), id));
    }

    @GET
    @Path("/groups/{conversationId}/announcement")
    public ApiResponse<String> getAnnouncement(@PathParam("conversationId") long conversationId, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.getGroupAnnouncement(SessionSupport.requireUserId(request), conversationId));
    }

    @PUT
    @Path("/groups/{conversationId}/announcement")
    public ApiResponse<Void> updateAnnouncement(@PathParam("conversationId") long conversationId, String content, @Context HttpServletRequest request) {
        chatService.updateGroupAnnouncement(SessionSupport.requireUserId(request), conversationId, content);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/conversations/{id}/heatmap")
    public ApiResponse<List<DailyMessageCount>> heatmap(@PathParam("id") long id, @Context HttpServletRequest request) {
        return ApiResponse.ok(chatService.heatmap(SessionSupport.requireUserId(request), id));
    }

    @GET
    @Path("/conversations/{id}/export")
    @Produces("text/html")
    public Response export(@PathParam("id") long id, @Context HttpServletRequest request) {
        String html = chatService.exportHtml(SessionSupport.requireUserId(request), id);
        return Response.ok(html.getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"chat-history-" + id + ".html\"")
                .type("text/html;charset=UTF-8")
                .build();
    }
}
