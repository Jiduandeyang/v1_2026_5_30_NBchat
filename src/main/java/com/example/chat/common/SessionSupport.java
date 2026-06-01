package com.example.chat.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
}
