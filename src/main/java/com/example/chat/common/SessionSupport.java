package com.example.chat.common;

import com.example.chat.model.User;
import com.example.chat.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.Response;

public final class SessionSupport {
    private SessionSupport() {
    }

    public static long requireUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw AppException.unauthorized();
        }
        Object value = session.getAttribute(SessionKeys.USER_ID);
        if (value instanceof Long userId) {
            return userId;
        }
        if (value instanceof Integer userId) {
            return userId.longValue();
        }
        throw AppException.unauthorized();
    }

    public static long requireAdmin(HttpServletRequest request) {
        long userId = requireUserId(request);
        User user = new UserService().get(userId);
        if (user == null || !"ADMIN".equals(user.role())) {
            throw new AppException(Response.Status.FORBIDDEN, "Admin permission required.");
        }
        if (user.disabled()) {
            throw new AppException(Response.Status.FORBIDDEN, "Account is disabled.");
        }
        return userId;
    }
}
