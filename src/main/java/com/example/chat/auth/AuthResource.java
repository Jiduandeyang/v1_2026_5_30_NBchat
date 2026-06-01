package com.example.chat.auth;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.AppException;
import com.example.chat.common.RateLimiter;
import com.example.chat.common.SessionKeys;
import com.example.chat.common.SessionSupport;
import com.example.chat.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final RateLimiter LOGIN_LIMITER = new RateLimiter(8, Duration.ofMinutes(5));
    private static final RateLimiter EMAIL_CODE_LIMITER = new RateLimiter(3, Duration.ofMinutes(10));

    private final AuthService authService = new AuthService();

    @POST
    @Path("/register/code")
    public ApiResponse<Void> sendRegisterCode(EmailCodeRequest request, @Context HttpServletRequest httpRequest) {
        requireRateLimit(EMAIL_CODE_LIMITER, clientKey(httpRequest, request.qqEmail()));
        authService.sendRegisterCode(request.qqEmail());
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/register")
    public ApiResponse<User> register(RegisterRequest request, @Context HttpServletRequest httpRequest) {
        User user = authService.register(request);
        httpRequest.getSession(true).setAttribute(SessionKeys.USER_ID, user.id());
        return ApiResponse.ok(user);
    }

    @POST
    @Path("/login")
    public ApiResponse<User> login(LoginRequest request, @Context HttpServletRequest httpRequest) {
        requireRateLimit(LOGIN_LIMITER, clientKey(httpRequest, request.username()));
        User user = authService.login(request);
        httpRequest.getSession(true).setAttribute(SessionKeys.USER_ID, user.id());
        return ApiResponse.ok(user);
    }

    @POST
    @Path("/logout")
    public ApiResponse<Void> logout(@Context HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ApiResponse.ok(null);
    }

    @GET
    @Path("/me")
    public ApiResponse<Long> me(@Context HttpServletRequest request) {
        return ApiResponse.ok(SessionSupport.requireUserId(request));
    }

    @POST
    @Path("/password-reset/code")
    public ApiResponse<Void> sendResetCode(EmailCodeRequest request, @Context HttpServletRequest httpRequest) {
        requireRateLimit(EMAIL_CODE_LIMITER, clientKey(httpRequest, request.qqEmail()));
        authService.sendResetCode(request.qqEmail());
        return ApiResponse.ok(null);
    }

    @POST
    @Path("/password-reset")
    public ApiResponse<Void> reset(PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok(null);
    }

    private static void requireRateLimit(RateLimiter limiter, String key) {
        if (!limiter.tryAcquire(key)) {
            throw new AppException(Response.Status.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }
    }

    private static String clientKey(HttpServletRequest request, String subject) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return ip + ":" + (subject == null ? "" : subject.trim().toLowerCase());
    }
}
