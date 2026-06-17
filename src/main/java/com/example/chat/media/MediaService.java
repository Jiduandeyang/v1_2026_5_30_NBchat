package com.example.chat.media;

import com.example.chat.common.AppException;
import com.example.chat.config.AppConfig;
import com.example.chat.config.Database;
import com.example.chat.model.MediaFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Locale;
import java.util.UUID;

public class MediaService {
    private final MediaDao mediaDao = new MediaDao();

    public MediaFile save(long ownerId, UploadKind kind, String originalName, String contentType, InputStream inputStream, long sizeBytes) {
        if (!UploadSizePolicy.allowsReportedSize(kind, sizeBytes)) {
            throw AppException.badRequest("File exceeds " + kind.maxBytes() + " bytes.");
        }
        if (!MediaContentTypePolicy.allows(kind, contentType)) {
            throw AppException.badRequest("Unsupported upload type.");
        }
        String safeName = originalName == null ? "upload.bin" : Path.of(originalName).getFileName().toString();
        String extension = extensionForContentType(contentType);
        if (extension.isBlank()) {
            int dot = safeName.lastIndexOf('.');
            if (dot >= 0) {
                extension = safeName.substring(dot).toLowerCase(Locale.ROOT);
            }
        }
        String storedName = UUID.randomUUID() + extension;
        Path folder = Path.of(AppConfig.get("upload.root", "uploads"), kind.name().toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(folder);
            Path storedPath = folder.resolve(storedName);
            Files.copy(inputStream, storedPath);
            long storedSize = Files.size(storedPath);
            if (!UploadSizePolicy.allowsStoredSize(kind, storedSize)) {
                Files.deleteIfExists(storedPath);
                throw AppException.badRequest("File exceeds " + kind.maxBytes() + " bytes.");
            }
            String url = MediaUrlBuilder.build(AppConfig.get("public.baseUrl", ""), kind, storedName);
            try (Connection connection = Database.connection()) {
                return mediaDao.create(connection, ownerId, kind, safeName, storedName, url,
                        contentType == null ? "application/octet-stream" : contentType, storedSize);
            }
        } catch (IOException exception) {
            throw AppException.badRequest("Failed to store upload.");
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    private String extensionForContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "audio/webm" -> ".webm";
            case "audio/ogg" -> ".ogg";
            case "audio/mp4" -> ".m4a";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> "";
        };
    }
}
