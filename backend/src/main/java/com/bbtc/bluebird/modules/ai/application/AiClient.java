package com.bbtc.bluebird.modules.ai.application;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import com.bbtc.bluebird.modules.ai.dto.AiModelTestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI 兼容的 Chat Completions 客户端。API Key 只从后端解密后使用。 */
@Service
@RequiredArgsConstructor
public class AiClient {

    private final ObjectMapper objectMapper;

    public record AiChatResult(String content, int inputTokens, int outputTokens) {
    }

    public AiChatResult chat(AiModelConfig model, String apiKey, String system, String user, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", model.getTemperature() == null ? 0.3 : model.getTemperature());
        body.put("max_tokens", maxTokens);

        JsonNode root = post(model, apiKey, body);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回内容为空");
        }
        JsonNode usage = root.path("usage");
        return new AiChatResult(
                content,
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0));
    }

    public AiModelTestResult test(AiModelConfig model, String apiKey) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModel());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a connectivity tester."),
                    Map.of("role", "user", "content", "Reply with OK.")));
            body.put("temperature", 0);
            body.put("max_tokens", 8);
            JsonNode root = post(model, apiKey, body);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return new AiModelTestResult(true, content.isBlank() ? "连接成功" : content,
                    System.currentTimeMillis() - start, model.getModel());
        } catch (Exception e) {
            return new AiModelTestResult(false, e.getMessage() == null ? "连接失败" : e.getMessage(),
                    System.currentTimeMillis() - start, model.getModel());
        }
    }

    private JsonNode post(AiModelConfig model, String apiKey, Map<String, Object> body) {
        int timeout = model.getTimeoutSeconds() == null ? 60 : Math.max(5, model.getTimeoutSeconds());
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeout, 20)))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint(model.getBaseUrl())))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 服务返回 HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 服务错误：" + abbreviate(root.path("error").toString()));
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 调用被中断");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "AI 调用失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** 允许管理员填写基础地址，也允许直接填写完整的 /chat/completions 地址。 */
    static String endpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "…";
    }
}
