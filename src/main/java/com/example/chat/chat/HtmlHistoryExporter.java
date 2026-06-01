package com.example.chat.chat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HtmlHistoryExporter {
    public record ExportMessage(String senderName, String type, String content, LocalDateTime sentAt) {
    }

    public String export(String title, List<ExportMessage> messages) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(escape(title)).append("</title>")
                .append("<style>body{font-family:Arial,'Microsoft YaHei',sans-serif;background:#f7faf9;color:#273333;padding:24px}")
                .append(".chat-message{background:white;border:1px solid #dfe8e5;border-radius:10px;margin:12px 0;padding:12px 14px}")
                .append(".meta{font-size:12px;color:#74827e;margin-bottom:6px}.image{max-width:260px;border-radius:8px}</style>")
                .append("</head><body><h1>").append(escape(title)).append("</h1>");
        for (ExportMessage message : messages) {
            html.append("<article class=\"chat-message\"><div class=\"meta\">")
                    .append(escape(message.senderName()))
                    .append(" · ")
                    .append(message.sentAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append("</div>");
            if ("IMAGE".equalsIgnoreCase(message.type())) {
                html.append("<img class=\"image\" src=\"").append(escapeAttribute(message.content())).append("\" alt=\"chat image\">");
            } else {
                html.append("<div>").append(escape(message.content())).append("</div>");
            }
            html.append("</article>");
        }
        return html.append("</body></html>").toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
