package com.example.chat.moment;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.MomentCommentView;
import com.example.chat.model.MomentView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/moments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MomentResource {
    private final MomentService momentService = new MomentService();

    @GET
    public ApiResponse<List<MomentView>> feed(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("beforeId") Long beforeId,
            @Context HttpServletRequest request) {
        return ApiResponse.ok(momentService.feed(SessionSupport.requireUserId(request), limit, beforeId));
    }

    @POST
    public ApiResponse<Long> create(MomentCreateRequest create, @Context HttpServletRequest request) {
        return ApiResponse.ok(momentService.create(SessionSupport.requireUserId(request), create));
    }

    @POST
    @Path("/{id}/likes")
    public ApiResponse<Void> like(@PathParam("id") long id, @Context HttpServletRequest request) {
        momentService.like(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @DELETE
    @Path("/{id}/likes")
    public ApiResponse<Void> unlike(@PathParam("id") long id, @Context HttpServletRequest request) {
        momentService.unlike(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/{id}/comments")
    public ApiResponse<List<MomentCommentView>> comments(@PathParam("id") long id, @Context HttpServletRequest request) {
        return ApiResponse.ok(momentService.comments(SessionSupport.requireUserId(request), id));
    }

    @POST
    @Path("/{id}/comments")
    public ApiResponse<Void> comment(@PathParam("id") long id, CommentRequest comment, @Context HttpServletRequest request) {
        momentService.comment(SessionSupport.requireUserId(request), id, comment.content());
        return ApiResponse.ok(null);
    }

    @DELETE
    @Path("/{id}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathParam("commentId") long commentId, @Context HttpServletRequest request) {
        momentService.deleteComment(SessionSupport.requireUserId(request), commentId);
        return ApiResponse.ok(null);
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<Void> delete(@PathParam("id") long id, @Context HttpServletRequest request) {
        momentService.delete(SessionSupport.requireUserId(request), id);
        return ApiResponse.ok(null);
    }
}
