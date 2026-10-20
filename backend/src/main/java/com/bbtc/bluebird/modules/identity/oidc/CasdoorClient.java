package com.bbtc.bluebird.modules.identity.oidc;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Casdoor HTTP 客户端（OIDC 授权码流 + 管理 API）。
 *
 * <p>端点按 Casdoor 约定由根地址派生，不依赖 discovery（部分内网部署关闭了
 * {@code /.well-known}）；连通性测试另走 {@code CasdoorConnectivityTester}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasdoorClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final ConfigService config;
    private final ObjectMapper objectMapper;

    /** 根地址（去掉尾部斜杠）；未配置时抛业务异常。 */
    public String endpoint() {
        String ep = config.get("casdoor", "endpoint", "");
        if (ep == null || ep.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "Casdoor 服务地址未配置");
        }
        String u = ep.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public boolean configured() {
        String ep = config.get("casdoor", "endpoint", "");
        String id = config.get("casdoor", "client-id", "");
        String secret = config.get("casdoor", "client-secret", "");
        return ep != null && !ep.isBlank() && id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }

    public String clientId() {
        return config.get("casdoor", "client-id", "");
    }

    public String organization() {
        return config.get("casdoor", "organization", "");
    }

    public String redirectUri() {
        return config.get("casdoor", "redirect-uri", "");
    }

    public int batchSize() {
        return Math.max(1, Math.min(500, config.getInt("orgsync", "batch-size", 100)));
    }

    /**
     * 拼接授权页地址。
     *
     * <p>逐参数手工编码：{@code UriComponentsBuilder.build(true)} 会把这些值当作**已编码**处理，
     * 而 {@code scope} 含空格（"openid profile email"）、{@code redirect_uri} 含 {@code ://}，
     * 校验直接抛 {@code IllegalArgumentException}，最终被兜底成「参数校验失败」——授权页永远打不开。
     */
    public String authorizeUrl(String state, String redirectUri) {
        String scopes = config.get("casdoor", "scopes", "openid profile email phone");
        return endpoint() + "/login/oauth/authorize"
                + "?client_id=" + enc(clientId())
                + "&response_type=code"
                + "&redirect_uri=" + enc(nz(redirectUri))
                + "&scope=" + enc(scopes)
                + "&state=" + enc(nz(state));
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    /** 退出登录跳转（清 Casdoor 侧会话）。 */
    public String logoutUrl(String postLogoutRedirect) {
        try {
            return endpoint() + "/login/oauth/logout?post_logout_redirect_uri="
                    + URLEncoder.encode(postLogoutRedirect, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 授权码换 token。 */
    public Map<String, Object> exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId());
        form.add("client_secret", config.get("casdoor", "client-secret", ""));
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            Map<String, Object> body = client().post()
                    .uri(endpoint() + "/api/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            if (body == null || body.get("access_token") == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "Casdoor 未返回 access_token");
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Casdoor 换取 token 失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "换取 token 失败：" + e.getMessage());
        }
    }

    /** 拉取当前用户信息。 */
    public Map<String, Object> userInfo(String accessToken) {
        try {
            Map<String, Object> body = client().get()
                    .uri(endpoint() + "/api/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MAP_TYPE);
            if (body == null || body.isEmpty()) {
                throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "Casdoor 未返回用户信息");
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Casdoor 拉取 userinfo 失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "拉取用户信息失败：" + e.getMessage());
        }
    }

    // ==================== 管理 API（同步用） ====================

    /**
     * Casdoor 管理 API 鉴权：官方 SDK 采用 `clientId` + `clientSecret` 查询参数，
     * 同时补一个 HTTP Basic 头（部分版本优先读 Basic）。两者并存不冲突。
     */
    private String authQuery() {
        return "clientId=" + enc(clientId()) + "&clientSecret=" + enc(config.get("casdoor", "client-secret", ""));
    }

    private String basicAuth() {
        String raw = clientId() + ":" + config.get("casdoor", "client-secret", "");
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public List<Map<String, Object>> getOrganizations() {
        return list(endpoint() + "/api/get-organizations?owner=admin&" + authQuery());
    }

    public List<Map<String, Object>> getGroups() {
        String owner = organization();
        return list(endpoint() + "/api/get-groups?"
                + (owner.isBlank() ? "" : "owner=" + enc(owner) + "&") + authQuery());
    }

    /** 分页拉全量用户（Casdoor 返回数组；不足一页即结束）。 */
    public List<Map<String, Object>> getAllUsers() {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        int size = batchSize();
        while (true) {
            String owner = organization();
            String url = endpoint() + "/api/get-users?p=" + page + "&pageSize=" + size
                    + (owner.isBlank() ? "" : "&owner=" + enc(owner))
                    + "&" + authQuery();
            List<Map<String, Object>> batch = list(url);
            all.addAll(batch);
            if (batch.size() < size || page > 200) {
                break;
            }
            page++;
        }
        return all;
    }

    private List<Map<String, Object>> list(String url) {
        try {
            String body = client().get().uri(url)
                    .header("Authorization", basicAuth())
                    .retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "[]" : body);
            JsonNode arr = node.isArray() ? node : node.get("data");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : arr) {
                out.add(objectMapper.convertValue(item, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                }));
            }
            return out;
        } catch (Exception e) {
            log.warn("Casdoor 列表接口失败 {}：{}", url, e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "拉取 Casdoor 数据失败：" + e.getMessage());
        }
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }

    private String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
