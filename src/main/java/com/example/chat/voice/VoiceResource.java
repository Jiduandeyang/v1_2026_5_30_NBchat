package com.example.chat.voice;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.VoiceCallSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/voice")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VoiceResource {
    private final VoiceService voiceService = new VoiceService();

    @POST
    @Path("/calls/{calleeId}")
    public ApiResponse<Long> start(@PathParam("calleeId") long calleeId,
                                   VoiceCallStartRequest startRequest,
                                   @Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.start(SessionSupport.requireUserId(request), calleeId, startRequest));
    }

    @GET
    @Path("/ice-servers")
    public ApiResponse<List<IceServerConfig.IceServer>> iceServers() {
        return ApiResponse.ok(voiceService.iceServers());
    }

    @GET
    @Path("/calls/incoming")
    public ApiResponse<VoiceCallSession> incoming(@Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.incoming(SessionSupport.requireUserId(request)));
    }

    @GET
    @Path("/calls/{callId}")
    public ApiResponse<VoiceCallSession> find(@PathParam("callId") long callId, @Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.findForUser(SessionSupport.requireUserId(request), callId));
    }

    @POST
    @Path("/calls/{callId}/accept")
    public ApiResponse<VoiceCallSession> accept(@PathParam("callId") long callId, @Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.accept(SessionSupport.requireUserId(request), callId));
    }

    @POST
    @Path("/calls/{callId}/reject")
    public ApiResponse<VoiceCallSession> reject(@PathParam("callId") long callId, @Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.reject(SessionSupport.requireUserId(request), callId));
    }

    @POST
    @Path("/calls/{callId}/end")
    public ApiResponse<VoiceCallSession> end(@PathParam("callId") long callId, @Context HttpServletRequest request) {
        return ApiResponse.ok(voiceService.end(SessionSupport.requireUserId(request), callId));
    }
}
