package com.example.chat.media;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadStaticServletContractTest {
    @Test
    void staticUploadsSupportAudioRangePlayback() throws IOException {
        String servlet = Files.readString(Path.of("src/main/java/com/example/chat/media/UploadStaticServlet.java"), StandardCharsets.UTF_8);

        assertTrue(servlet.contains("Range"));
        assertTrue(servlet.contains("SC_PARTIAL_CONTENT"));
        assertTrue(servlet.contains("Content-Range"));
        assertTrue(servlet.contains("suffixLength"));
        assertTrue(servlet.contains("Math.max(size - suffixLength, 0)"));
        assertTrue(servlet.contains("audio/webm"));
        assertTrue(servlet.contains("audio/ogg"));
        assertTrue(servlet.contains("audio/mp4"));
        String service = Files.readString(Path.of("src/main/java/com/example/chat/media/MediaService.java"), StandardCharsets.UTF_8);
        assertTrue(service.contains("extensionForContentType"));
        assertTrue(service.contains("audio/mp4"));
        assertTrue(service.contains(".m4a"));
    }
}
