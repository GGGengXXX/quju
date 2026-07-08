package cn.edu.buaa.quju.module.social.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ChatAiService {
    private static final String DEFAULT_SYSTEM_PROMPT = "你是聊天回复助手。请基于上下文生成一条自然、得体、简洁的中文回复草稿，不要解释，不要加引号。";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;

    @Value("${quju.ai.base-url:}")
    private String baseUrl;

    @Value("${quju.ai.api-key:}")
    private String apiKey;

    @Value("${quju.ai.model:deepseek-v3.2}")
    private String model;

    public ChatAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generateReply(String userSystemPrompt, String conversationContext) {
        String systemPrompt = userSystemPrompt == null || userSystemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : DEFAULT_SYSTEM_PROMPT + "\n\n用户附加要求：" + userSystemPrompt.trim();
        try {
            String content = chat(systemPrompt, conversationContext);
            String normalized = content == null ? "" : content.trim();
            if (normalized.isEmpty()) {
                throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_empty_response");
            }
            return stripCodeFence(normalized);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_reply_failed");
        } catch (IOException e) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_reply_failed");
        }
    }

    private String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_not_configured");
        }
        Map<String, Object> body = Map.of(
                "model", model == null || model.isBlank() ? "deepseek-v3.2" : model,
                "temperature", 0.7,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_http_" + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_empty_response");
        }
        return content.asText();
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String stripCodeFence(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\n?", "");
            normalized = normalized.replaceFirst("\\n?```$", "");
        }
        return normalized.trim();
    }
}
