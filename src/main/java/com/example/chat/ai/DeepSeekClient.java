package com.example.chat.ai;

import com.example.chat.config.AppConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class DeepSeekClient {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Transport transport;

    public DeepSeekClient(String baseUrl, String apiKey, String model, Transport transport) {
        this.baseUrl = trimSlash(baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model.trim();
        this.transport = transport == null ? new HttpClientTransport(this.apiKey) : transport;
    }

    public static DeepSeekClient fromConfig() {
        return new DeepSeekClient(
                AppConfig.get("deepseek.baseUrl", "https://api.deepseek.com"),
                AppConfig.get("deepseek.apiKey", ""),
                AppConfig.get("deepseek.model", "deepseek-chat"),
                null
        );
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank() && transport instanceof HttpClientTransport) {
            return unavailableMessage();
        }
        try {
            String body = JSON.writeValueAsString(new ChatRequest(
                    model,
                    List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                    false
            ));
            String response = transport.postJson(URI.create(baseUrl + "/chat/completions"), body, TIMEOUT);
            ChatResponse chatResponse = JSON.readValue(response, ChatResponse.class);
            if (chatResponse.choices() == null || chatResponse.choices().isEmpty()) {
                return unavailableMessage();
            }
            Message message = chatResponse.choices().get(0).message();
            if (message == null || message.content() == null || message.content().isBlank()) {
                return unavailableMessage();
            }
            return message.content().trim();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return unavailableMessage();
        }
    }

    private String unavailableMessage() {
        return "DeepSeek AI 助手暂时不可用，请检查 deepseek.apiKey、deepseek.model 和网络连接。";
    }

    private static String trimSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @FunctionalInterface
    public interface Transport {
        String postJson(URI uri, String json, Duration timeout) throws IOException, InterruptedException;
    }

    private static class HttpClientTransport implements Transport {
        private final HttpClient client = HttpClient.newHttpClient();
        private final String apiKey;

        private HttpClientTransport(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public String postJson(URI uri, String json, Duration timeout) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("DeepSeek returned HTTP " + response.statusCode());
            }
            return response.body();
        }
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream) {
    }

    private record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }
}
