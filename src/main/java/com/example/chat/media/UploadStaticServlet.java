package com.example.chat.media;

import com.example.chat.config.AppConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/uploads/*")
public class UploadStaticServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.contains("..")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path root = Path.of(AppConfig.get("upload.root", "uploads")).toAbsolutePath().normalize();
        Path file = root.resolve(pathInfo.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = contentTypeFor(file);
        long size = Files.size(file);
        String range = request.getHeader("Range");
        response.setContentType(contentType == null ? "application/octet-stream" : contentType);
        response.setHeader("Accept-Ranges", "bytes");
        if (range != null && range.startsWith("bytes=")) {
            copyRange(file, size, range, response);
            return;
        }
        response.setContentLengthLong(size);
        Files.copy(file, response.getOutputStream());
    }

    private void copyRange(Path file, long size, String range, HttpServletResponse response) throws IOException {
        String[] parts = range.substring("bytes=".length()).split("-", 2);
        long start;
        long end;
        if (parts[0].isBlank()) {
            long suffixLength = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1]) : size;
            start = Math.max(size - suffixLength, 0);
            end = size - 1;
        } else {
            start = Long.parseLong(parts[0]);
            end = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1]) : size - 1;
        }
        if (start < 0 || end < start || start >= size) {
            response.setHeader("Content-Range", "bytes */" + size);
            response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
        }
        end = Math.min(end, size - 1);
        long length = end - start + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        response.setContentLengthLong(length);
        try (InputStream input = Files.newInputStream(file)) {
            input.skipNBytes(start);
            input.transferTo(new LimitedOutputStream(response.getOutputStream(), length));
        }
    }

    private static final class LimitedOutputStream extends java.io.OutputStream {
        private final java.io.OutputStream delegate;
        private long remaining;

        private LimitedOutputStream(java.io.OutputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public void write(int b) throws IOException {
            if (remaining <= 0) return;
            delegate.write(b);
            remaining--;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return;
            int allowed = (int) Math.min(len, remaining);
            delegate.write(b, off, allowed);
            remaining -= allowed;
        }
    }

    private String contentTypeFor(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".webm")) return "audio/webm";
        if (name.endsWith(".ogg") || name.endsWith(".oga")) return "audio/ogg";
        if (name.endsWith(".m4a") || name.endsWith(".mp4")) return "audio/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        return Files.probeContentType(file);
    }
}
