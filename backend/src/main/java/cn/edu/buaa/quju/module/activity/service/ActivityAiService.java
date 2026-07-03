package cn.edu.buaa.quju.module.activity.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ActivityAiService {
    private static final String USER_AGENT = "QuJu-Activity/1.0";
    private static final Logger log = LoggerFactory.getLogger(ActivityAiService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quju.ai.base-url:}")
    private String baseUrl;

    @Value("${quju.ai.api-key:}")
    private String apiKey;

    @Value("${quju.ai.model:deepseek-v3.2}")
    private String model;

    public AiPlanResult generatePlan(String theme, String category) {
        String normalizedTheme = theme == null || theme.isBlank() ? "兴趣活动" : theme.trim();
        try {
            String content = chatJson(
                    "你是活动策划助手。请直接输出一个 JSON 对象，不要解释，不要 markdown。",
                    """
                    为线下活动生成一个 JSON 对象，只返回 JSON，不要解释。
                    字段：name,intro,category,tags,capacity,fee,startTime,endTime,signupDeadline,city,address,safetyNotice。
                    约束：未来 3-14 天；tags 2-4 个；city/address 给具体建议；时间格式 yyyy-MM-ddTHH:mm:ss。
                    主题：%s
                    分类：%s
                    """.formatted(normalizedTheme, category == null ? "OTHER" : category.trim())
            );
            JsonNode root = objectMapper.readTree(extractJsonObject(content));
            List<String> tags = readTags(root.path("tags"), normalizedTheme);
            return new AiPlanResult(
                    text(root, "name", normalizedTheme + "活动"),
                    text(root, "intro", normalizedTheme + "主题线下交流活动"),
                    normalizeCategory(text(root, "category", category == null || category.isBlank() ? "OTHER" : category)),
                    tags,
                    parseInteger(root.path("capacity"), 20),
                    parseDecimal(root.path("fee"), BigDecimal.ZERO),
                    text(root, "startTime", LocalDateTime.now().plusDays(7).withHour(19).withMinute(0).withSecond(0).withNano(0).toString()),
                    text(root, "endTime", LocalDateTime.now().plusDays(7).withHour(21).withMinute(0).withSecond(0).withNano(0).toString()),
                    text(root, "signupDeadline", LocalDateTime.now().plusDays(5).withHour(18).withMinute(0).withSecond(0).withNano(0).toString()),
                    text(root, "city", "北京"),
                    text(root, "address", "北京市待确认活动地点"),
                    text(root, "safetyNotice", "请按时到场，注意出行安全与财物保管。")
            );
        } catch (Exception e) {
            log.warn("AI 生成活动草稿失败，使用兜底方案: {}", e.getMessage());
            return fallbackPlan(normalizedTheme, category);
        }
    }

    public AuditDecision auditActivity(String payload) {
        try {
            String content = chatJson(
                    "你是活动内容安全审核助手。请只返回 JSON 对象，不要解释。",
                    """
                    请审查以下活动内容是否适合在校园/城市青年兴趣社交平台发布。
                    输出 JSON：{"result":"PASSED|REJECTED|TO_MANUAL","reason":"不超过60字中文"}
                    规则：
                    1. 涉及违法、色情、暴力煽动、危险挑战、诈骗引流，返回 REJECTED。
                    2. 难以确认风险、存在灰区、涉及大规模组织风险，返回 TO_MANUAL。
                    3. 普通正常活动返回 PASSED。
                    活动内容：%s
                    """.formatted(payload)
            );
            JsonNode root = objectMapper.readTree(extractJsonObject(content));
            String result = text(root, "result", "TO_MANUAL").toUpperCase(Locale.ROOT);
            String reason = text(root, "reason", "AI 审核结果不确定，需人工复核");
            if (!List.of("PASSED", "REJECTED", "TO_MANUAL").contains(result)) {
                result = "TO_MANUAL";
            }
            return new AuditDecision(result, reason);
        } catch (Exception e) {
            log.warn("AI 审核失败，转人工复核: {}", e.getMessage());
            return new AuditDecision("TO_MANUAL", "AI 审核失败，转人工复核");
        }
    }

    public String classifyImage(String originalName, String imageUrl) {
        try {
            String content = chatJson(
                    "你是活动图片分类助手。请只返回 JSON 对象，不要解释。",
                    """
                    请根据图片文件名、地址关键词和活动总结场景，给图片分到以下一类：
                    GROUP_PHOTO, VENUE, PROCESS, MATERIAL, RESULT
                    输出 JSON：{"category":"枚举值","reason":"一句话"}
                    文件名：%s
                    图片地址：%s
                    """.formatted(blankToDash(originalName), blankToDash(imageUrl))
            );
            JsonNode root = objectMapper.readTree(extractJsonObject(content));
            String category = text(root, "category", "PROCESS").toUpperCase(Locale.ROOT);
            if (List.of("GROUP_PHOTO", "VENUE", "PROCESS", "MATERIAL", "RESULT").contains(category)) {
                return category;
            }
        } catch (Exception e) {
            log.warn("AI 图片分类失败，使用兜底分类: {}", e.getMessage());
        }
        return fallbackImageCategory(originalName, imageUrl);
    }

    private AiPlanResult fallbackPlan(String theme, String category) {
        String normalizedCategory = normalizeCategory(category == null || category.isBlank() ? "OTHER" : category);
        return new AiPlanResult(
                theme + "活动",
                theme + "主题线下交流活动，适合 8-20 人参与，节奏轻松，便于结识同好。",
                normalizedCategory,
                List.of(theme, "线下活动", "兴趣社交"),
                20,
                BigDecimal.ZERO,
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0).withSecond(0).withNano(0).toString(),
                LocalDateTime.now().plusDays(7).withHour(21).withMinute(0).withSecond(0).withNano(0).toString(),
                LocalDateTime.now().plusDays(5).withHour(18).withMinute(0).withSecond(0).withNano(0).toString(),
                "北京",
                "北京市待确认活动地点",
                "请按时到场，注意出行安全与财物保管。"
        );
    }

    private String fallbackImageCategory(String originalName, String imageUrl) {
        String lower = (blankToDash(originalName) + " " + blankToDash(imageUrl)).toLowerCase(Locale.ROOT);
        if (lower.contains("group") || lower.contains("team") || lower.contains("heying")) return "GROUP_PHOTO";
        if (lower.contains("venue") || lower.contains("site") || lower.contains("place")) return "VENUE";
        if (lower.contains("material") || lower.contains("equip")) return "MATERIAL";
        if (lower.contains("result") || lower.contains("award") || lower.contains("final")) return "RESULT";
        return "PROCESS";
    }

    private String chatJson(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        if (isBlank(baseUrl) || isBlank(apiKey)) throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "ai_not_configured");
        Map<String, Object> body = Map.of(
                "model", isBlank(model) ? "deepseek-v3.2" : model,
                "temperature", requestTemperature(),
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
                .header("User-Agent", USER_AGENT)
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
        return normalizeJsonContent(content.asText());
    }

    private String normalizeJsonContent(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\n?", "");
            value = value.replaceFirst("\n?```$", "");
        }
        return value.trim();
    }

    private String extractJsonObject(String content) {
        String value = normalizeJsonContent(content);
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        throw new IllegalArgumentException("AI 响应中未找到 JSON 对象");
    }

    private List<String> readTags(JsonNode node, String theme) {
        List<String> tags = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty() && !tags.contains(value)) tags.add(value);
            }
        } else if (!node.isMissingNode() && !node.asText("").isBlank()) {
            for (String item : node.asText("").split("[,，/\\s]+")) {
                String value = item.trim();
                if (!value.isEmpty() && !tags.contains(value)) tags.add(value);
            }
        }
        if (tags.isEmpty()) tags = List.of(theme, "线下活动", "兴趣社交");
        return tags;
    }

    private Integer parseInteger(JsonNode node, int fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        if (node.isInt() || node.isLong()) return node.asInt(fallback);
        String text = node.asText("").replaceAll("[^0-9]", "").trim();
        if (text.isEmpty()) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private BigDecimal parseDecimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        if (node.isNumber()) return node.decimalValue();
        String text = node.asText("").trim();
        if (text.isEmpty()) return fallback;
        String normalized = text.replace("免费", "0").replaceAll("[^0-9.-]", "");
        if (normalized.isEmpty() || "-".equals(normalized) || ".".equals(normalized)) return fallback;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? fallback : value;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToDash(String value) {
        return isBlank(value) ? "-" : value.trim();
    }

    private double requestTemperature() {
        return model != null && model.trim().toLowerCase(Locale.ROOT).startsWith("kimi-k2.5") ? 1.0 : 0.2;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SPORTS", "HIKING", "BOARD_GAME", "STUDY", "CHARITY", "CITY_WALK", "OTHER" -> normalized;
            default -> "OTHER";
        };
    }

    public record AiPlanResult(
            String name,
            String intro,
            String category,
            List<String> tags,
            Integer capacity,
            BigDecimal fee,
            String startTime,
            String endTime,
            String signupDeadline,
            String city,
            String address,
            String safetyNotice
    ) {}

    public record AuditDecision(String result, String reason) {}
}
