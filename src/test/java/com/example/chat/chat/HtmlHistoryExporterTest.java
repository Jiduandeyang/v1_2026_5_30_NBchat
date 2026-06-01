package com.example.chat.chat;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlHistoryExporterTest {
    @Test
    void exportEscapesHtmlAndKeepsMessageMarkup() {
        HtmlHistoryExporter exporter = new HtmlHistoryExporter();

        String html = exporter.export("Alice and Bob", List.of(
                new HtmlHistoryExporter.ExportMessage(
                        "Alice",
                        "TEXT",
                        "<script>alert(1)</script>",
                        LocalDateTime.of(2026, 5, 30, 10, 0)
                )
        ));

        assertTrue(html.contains("Alice and Bob"));
        assertTrue(html.contains("class=\"chat-message\""));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }
}
