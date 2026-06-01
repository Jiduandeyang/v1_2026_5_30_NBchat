package com.example.chat.media;

import com.example.chat.config.AppConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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
        String contentType = Files.probeContentType(file);
        response.setContentType(contentType == null ? "application/octet-stream" : contentType);
        Files.copy(file, response.getOutputStream());
    }
}
