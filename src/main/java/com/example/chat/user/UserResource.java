package com.example.chat.user;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    private final UserService userService = new UserService();

    @GET
    @Path("/me")
    public ApiResponse<User> me(@Context HttpServletRequest request) {
        return ApiResponse.ok(userService.get(SessionSupport.requireUserId(request)));
    }

    @PUT
    @Path("/me")
    public ApiResponse<User> update(ProfileUpdateRequest profile, @Context HttpServletRequest request) {
        return ApiResponse.ok(userService.update(SessionSupport.requireUserId(request), profile));
    }

    @GET
    @Path("/search")
    public ApiResponse<List<User>> search(@QueryParam("q") String q) {
        return ApiResponse.ok(userService.search(q));
    }

    @GET
    @Path("/{id}/profile")
    public ApiResponse<User> publicProfile(@PathParam("id") long id) {
        return ApiResponse.ok(userService.get(id));
    }
}
