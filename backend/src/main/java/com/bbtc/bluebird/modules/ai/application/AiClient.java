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
import java.util.ArrayList;
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
     * 连通性测试超时：测试只做轻量探测（GET /v1/models），不触发生成。
     * 生成可能因上游排队/推理耗时几十秒，交给真实对话与周报任务验证。
     */
    private static final int TEST_TIMEOUT_SECONDS = 8;

    public AiChatResult chat(AiModelConfig model, String apiKey, String system, String user, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", model.getTemperature() == null ? 0.3 : model.getTemperature());
        body.put("max_tokens", maxTokens);

        try {
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
        } catch (RuntimeException e) {
            // 成功不打印；只有失败才输出详细上下文与完整堆栈。
            log.error("AI 对话调用失败：id={}, name={}, model={}, endpoint={}, errorType={}, error={}",
                    model.getId(), model.getName(), model.getModel(),
                    endpoint(model.getBaseUrl()), e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 只探测接口连通性与模型可见性：GET /v1/models，秒级返回。
     * 通过不代表模型能生成内容，生成能力由真实对话/周报任务检验。
     */
    public AiModelTestResult test(AiModelConfig model, String apiKey) {
        long start = System.currentTimeMillis();
        String url = modelsEndpoint(model.getBaseUrl());
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        try {
            JsonNode root = getModels(model, apiKey);
            long latency = System.currentTimeMillis() - start;
            List<String> ids = new ArrayList<>();
            for (JsonNode node : root.path("data")) {
                String id = node.path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            if (ids.isEmpty()) {
                return new AiModelTestResult(true, "连接成功（接口未返回模型列表）", latency, model.getModel());
            }
            boolean visible = ids.stream().anyMatch(id -> id.equalsIgnoreCase(model.getModel()));
            if (visible) {
                return new AiModelTestResult(true, "连接成功，模型可用：" + model.getModel(), latency, model.getModel());
            }
            return new AiModelTestResult(false, "接口可访问，但模型列表中未找到「" + model.getModel()
                    + "」，请检查模型名或该 Key 的模型权限", latency, model.getModel());
        } catch (Exception e) {
            // 只有失败才打印：模型信息、地址、耗时、异常类型、消息与完整堆栈。
            log.error("AI 模型测试失败：id={}, name={}, model={}, endpoint={}, timeout={}s, hasApiKey={}, "
                            + "latency={}ms, errorType={}, error={}",
                    model.getId(), model.getName(), model.getModel(), url, TEST_TIMEOUT_SECONDS, hasKey,
                    System.currentTimeMillis() - start, e.getClass().getSimpleName(), e.getMessage(), e);
            return new AiModelTestResult(false, e.getMessage() == null ? "连接失败" : e.getMessage(),
                    System.currentTimeMillis() - start, model.getModel());
        }
    }

    private JsonNode getModels(AiModelConfig model, String apiKey) {
        URI uri = URI.create(modelsEndpoint(model.getBaseUrl()));
        // HttpClient 在 Java 21 是 AutoCloseable：每次探测新建一个就关一个，避免线程与连接累积。
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .build()) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                    .header("Accept", "application/json")
                    .GET();
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

    private JsonNode post(AiModelConfig model, String apiKey, Map<String, Object> body) {
        int timeout = model.getTimeoutSeconds() == null ? 60 : Math.max(5, model.getTimeoutSeconds());
        URI uri = URI.create(endpoint(model.getBaseUrl()));
        try {
            String json = objectMapper.writeValueAsString(body);
            boolean hasKey = apiKey != null && !apiKey.isBlank();

            // 同上：Java 21 的 HttpClient 可关闭，用完即闭。
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeout, 20)))
                    .build()) {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                if (hasKey) {
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
            }
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

    /** 由对话地址反推模型列表地址，用于轻量连通性探测。 */
    static String modelsEndpoint(String baseUrl) {
        String chat = endpoint(baseUrl);
        return chat.substring(0, chat.length() - "/chat/completions".length()) + "/models";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "…";
    }
}
