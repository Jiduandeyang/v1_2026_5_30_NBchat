package com.example.chat.friend;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.FriendGroup;
import com.example.chat.model.FriendRequestView;
import com.example.chat.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FriendResource {
    private final FriendService friendService = new FriendService();

    @GET
    @Path("/friend-groups")
    public ApiResponse<List<FriendGroup>> groups(@Context HttpServletRequest request) {
        return ApiResponse.ok(friendService.groups(SessionSupport.requireUserId(request)));
    }

    @POST
    @Path("/friend-groups")
    public ApiResponse<Long> createGroup(FriendGroupRequest group, @Context HttpServletRequest request) {
        return ApiResponse.ok(friendService.createGroup(SessionSupport.requireUserId(request), group.name()));
    }

    @PUT
    @Path("/friend-groups/{id}")
    public ApiResponse<Void> renameGroup(@PathParam("id") long groupId, FriendGroupRequest group, @Context HttpServletRequest request) {
        friendService.renameGroup(SessionSupport.requireUserId(request), groupId, group.name());
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/friends")
    public ApiResponse<List<User>> friends(@Context HttpServletRequest request) {
        return ApiResponse.ok(friendService.friends(SessionSupport.requireUserId(request)));
    }

    @PUT
    @Path("/friends/{id}/group")
    public ApiResponse<Void> move(@PathParam("id") long friendId, MoveFriendRequest move, @Context HttpServletRequest request) {
        friendService.moveFriend(SessionSupport.requireUserId(request), friendId, move.groupId());
        return ApiResponse.ok(null);
    }

    @PUT
    @Path("/friends/{id}/close-friend")
    public ApiResponse<Void> closeFriend(@PathParam("id") long friendId, CloseFriendRequest closeFriend, @Context HttpServletRequest request) {
        friendService.setCloseFriend(SessionSupport.requireUserId(request), friendId, closeFriend.closeFriend());
        return ApiResponse.ok(null);
    }

    @DELETE
    @Path("/friends/{id}")
    public ApiResponse<Void> delete(@PathParam("id") long friendId, @Context HttpServletRequest request) {
        friendService.deleteFriend(SessionSupport.requireUserId(request), friendId);
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/friend-requests")
    public ApiResponse<Void> send(FriendRequestCreate create, @Context HttpServletRequest request) {
        friendService.sendRequest(SessionSupport.requireUserId(request), create);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/friend-requests")
    public ApiResponse<List<FriendRequestView>> requests(@QueryParam("mode") @DefaultValue("received") String mode, @Context HttpServletRequest request) {
        return ApiResponse.ok(friendService.requests(SessionSupport.requireUserId(request), mode));
    }

    @POST
    @Path("/friend-requests/{id}/accept")
    public ApiResponse<Void> accept(@PathParam("id") long id, @Context HttpServletRequest request) {
        friendService.accept(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/friend-requests/{id}/reject")
    public ApiResponse<Void> reject(@PathParam("id") long id, @Context HttpServletRequest request) {
        friendService.reject(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }
}
