package com.bbtc.bluebird.modules.ai.application;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import com.bbtc.bluebird.modules.ai.dto.AiModelTestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClient {

    private final ObjectMapper objectMapper;

    public record AiChatResult(String content, int inputTokens, int outputTokens) {
    }

    /**
     * 连通性测试的固定超时：前端请求 20s 超时，测试必须在此之前返回，
     * 否则用户只看到「请求超时」，拿不到模型服务真正的错误信息。
     */
    private static final int TEST_TIMEOUT_SECONDS = 12;

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
        String url = endpoint(model.getBaseUrl());
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        log.info("AI 模型测试开始：id={}, name={}, model={}, endpoint={}, timeout={}s, hasApiKey={}",
                model.getId(), model.getName(), model.getModel(), url, TEST_TIMEOUT_SECONDS, hasKey);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModel());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a connectivity tester."),
                    Map.of("role", "user", "content", "Reply with OK.")));
            body.put("temperature", 0);
            body.put("max_tokens", 8);
            JsonNode root = post(model, apiKey, body, TEST_TIMEOUT_SECONDS);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            long latency = System.currentTimeMillis() - start;
            log.info("AI 模型测试成功：id={}, model={}, endpoint={}, latency={}ms, reply={}",
                    model.getId(), model.getModel(), url, latency, abbreviate(content));
            return new AiModelTestResult(true, content.isBlank() ? "连接成功" : content, latency, model.getModel());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("AI 模型测试失败：id={}, name={}, model={}, endpoint={}, latency={}ms, errorType={}, error={}",
                    model.getId(), model.getName(), model.getModel(), url, latency,
                    e.getClass().getSimpleName(), e.getMessage());
            // 详细堆栈放在 DEBUG：排查连接/证书/DNS 问题时打开 debug 即可看到完整链路。
            log.debug("AI 模型测试异常堆栈", e);
            return new AiModelTestResult(false, e.getMessage() == null ? "连接失败" : e.getMessage(),
                    latency, model.getModel());
        }
    }

    private JsonNode post(AiModelConfig model, String apiKey, Map<String, Object> body) {
        return post(model, apiKey, body, null);
    }

    private JsonNode post(AiModelConfig model, String apiKey, Map<String, Object> body, Integer timeoutOverride) {
        int timeout = timeoutOverride != null
                ? Math.max(3, timeoutOverride)
                : (model.getTimeoutSeconds() == null ? 60 : Math.max(5, model.getTimeoutSeconds()));
        URI uri = URI.create(endpoint(model.getBaseUrl()));
        long requestStart = System.currentTimeMillis();
        try {
            String json = objectMapper.writeValueAsString(body);
            boolean hasKey = apiKey != null && !apiKey.isBlank();
            log.info("AI 请求：endpoint={}, model={}, timeout={}s, bodyBytes={}, hasApiKey={}",
                    uri, model.getModel(), timeout, json.getBytes(StandardCharsets.UTF_8).length, hasKey);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeout, 20)))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (hasKey) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = System.currentTimeMillis() - requestStart;
            log.info("AI 响应：endpoint={}, model={}, status={}, latency={}ms, bodyBytes={}",
                    uri, model.getModel(), response.statusCode(), latency,
                    response.body() == null ? 0 : response.body().getBytes(StandardCharsets.UTF_8).length);
            log.debug("AI 响应正文：endpoint={}, body={}", uri, abbreviate(response.body()));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI 服务返回非 2xx：endpoint={}, status={}, body={}",
                        uri, response.statusCode(), abbreviate(response.body()));
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 服务返回 HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                log.warn("AI 服务返回 error 字段：endpoint={}, error={}", uri, abbreviate(root.path("error").toString()));
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 服务错误：" + abbreviate(root.path("error").toString()));
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 调用被中断：endpoint={}, elapsed={}ms", uri,
                    System.currentTimeMillis() - requestStart);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 调用被中断");
        } catch (Exception e) {
            log.warn("AI 调用异常：endpoint={}, elapsed={}ms, errorType={}, error={}",
                    uri, System.currentTimeMillis() - requestStart, e.getClass().getSimpleName(), e.getMessage());
            log.debug("AI 调用异常堆栈", e);
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
