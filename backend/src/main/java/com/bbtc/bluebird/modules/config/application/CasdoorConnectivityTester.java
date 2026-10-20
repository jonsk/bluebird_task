package com.bbtc.bluebird.modules.config.application;

import com.bbtc.bluebird.modules.config.dto.ConfigTestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Casdoor 连通性测试：拉取 OIDC discovery 文档，验证服务地址可达、端点齐全。
 *
 * <p>只做只读探测，不发起授权码流程（无法在无用户交互时验证 client-secret）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasdoorConnectivityTester {

    private final ConfigService config;
    private final ObjectMapper objectMapper;

    public ConfigTestResult test() {
        String endpoint = trimSlash(config.get("casdoor", "endpoint", ""));
        if (endpoint.isEmpty()) {
            return new ConfigTestResult(false, "请先填写 Casdoor 服务地址", 0, null, null, null);
        }
        String clientId = config.get("casdoor", "client-id", "");
        String secret = config.get("casdoor", "client-secret", "");
        long start = System.currentTimeMillis();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(8));
            factory.setReadTimeout(Duration.ofSeconds(8));
            RestClient client = RestClient.builder().requestFactory(factory).build();
            String body = client.get()
                    .uri(endpoint + "/.well-known/openid-configuration")
                    .retrieve()
                    .body(String.class);
            long cost = System.currentTimeMillis() - start;
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            String issuer = text(node, "issuer");
            String authorize = text(node, "authorization_endpoint");
            String token = text(node, "token_endpoint");
            boolean endpointsOk = authorize != null && token != null;
            String msg;
            if (!endpointsOk) {
                msg = "服务可达，但 discovery 文档缺少 authorization_endpoint / token_endpoint";
            } else if (clientId.isBlank() || secret.isBlank()) {
                msg = "服务可达；请补充 Client ID 与 Client Secret 后保存";
            } else {
                msg = "服务可达，端点齐全";
            }
            return new ConfigTestResult(endpointsOk, msg, cost, issuer, authorize, token);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("Casdoor 连通性测试失败：{}", e.getMessage());
            return new ConfigTestResult(false, "连接失败：" + rootMessage(e), cost, null, null, null);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String trimSlash(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
