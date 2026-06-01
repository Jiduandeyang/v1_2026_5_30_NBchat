package com.example.chat.model;

public record MediaFile(
        long id,
        long ownerId,
        String kind,
        String originalName,
        String url,
        String contentType,
        long sizeBytes
) {
}
