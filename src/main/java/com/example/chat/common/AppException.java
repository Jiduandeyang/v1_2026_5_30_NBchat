package com.example.chat.common;

import jakarta.ws.rs.core.Response;

public class AppException extends RuntimeException {
    private final Response.Status status;

    public AppException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    public Response.Status status() {
        return status;
    }

    public static AppException badRequest(String message) {
        return new AppException(Response.Status.BAD_REQUEST, message);
    }

    public static AppException unauthorized() {
        return new AppException(Response.Status.UNAUTHORIZED, "Please login first.");
    }
}
