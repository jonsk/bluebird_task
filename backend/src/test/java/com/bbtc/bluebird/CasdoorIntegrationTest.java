package com.bbtc.bluebird;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casdoor 接入的端到端冒烟（配置中心 + 登录方式切换 + 组织同步模式）。
 *
 * <p>不依赖真实 Casdoor：只验证「配置读写 → 生效路径」这条链路，
 * 外部 HTTP 调用由 CasdoorClient 在未配置时以业务异常短路（已由单测覆盖语义）。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456",
        "springdoc.api-docs.enabled=false",
        "app.identity.provider=LOCAL",
        "app.orgsync.mode=NONE"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CasdoorIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-casdoor-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Test123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    @Order(1)
    void configRequiresAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @Order(2)
    void configGroupsExposeRegistry() throws Exception {
        String body = mvc.perform(get("/api/v1/admin/config")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode groups = objectMapper.readTree(body).path("data");
        assertEquals(6, groups.size(), "六个配置分组：casdoor/identity/orgsync/security/audit/storage");

        JsonNode casdoor = null;
        for (JsonNode g : groups) {
            if ("casdoor".equals(g.path("group").asText())) {
                casdoor = g;
            }
        }
        assertTrue(casdoor != null, "必须包含 casdoor 分组");
        boolean secretRegistered = false;
        for (JsonNode item : casdoor.path("items")) {
            if ("client-secret".equals(item.path("key").asText())) {
                assertTrue(item.path("secret").asBoolean(), "client-secret 必须登记为密文项");
                // 初始库中无值：value 为空、hasSecret=false（有值后由 Order(3) 验证只返回掩码）
                assertEquals("", item.path("value").asText());
                assertFalse(item.path("hasSecret").asBoolean());
                secretRegistered = true;
            }
        }
        assertTrue(secretRegistered, "client-secret 必须登记为密文项");
    }

    @Test
    @Order(3)
    void saveCasdoorSecretAndKeepMaskOnReuse() throws Exception {
        String token = adminToken();

        mvc.perform(put("/api/v1/admin/config/casdoor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"values":{"endpoint":"https://casdoor.example.com","client-id":"bb-client",
                                "client-secret":"s3cr3t-value","organization":"bluebird"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.key=='client-secret')].value").value("******"))
                .andExpect(jsonPath("$.data.items[?(@.key=='client-secret')].hasSecret").value(true))
                .andExpect(jsonPath("$.data.items[?(@.key=='client-secret')].source").value("DB"))
                .andExpect(jsonPath("$.data.items[?(@.key=='endpoint')].value").value("https://casdoor.example.com"));

        // 回传掩码表示「不修改」，不得把掩码写进库
        mvc.perform(put("/api/v1/admin/config/casdoor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"client-secret\":\"******\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.key=='client-secret')].hasSecret").value(true));
    }

    @Test
    @Order(4)
    void switchProviderToOidcReflectsInExternalConfig() throws Exception {
        String token = adminToken();

        mvc.perform(put("/api/v1/admin/config/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"provider\":\"OIDC\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String body = mvc.perform(get("/api/v1/auth/external/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("OIDC"))
                .andExpect(jsonPath("$.data.authorizeUrl").value("/api/v1/auth/oidc/redirect"))
                .andReturn().getResponse().getContentAsString();

        // 已配置 endpoint，故 logoutUrl 应可生成
        String logoutUrl = objectMapper.readTree(body).path("data").path("logoutUrl").asText("");
        assertTrue(logoutUrl.contains("/login/oauth/logout"), "OIDC 下应返回上游登出地址：" + logoutUrl);

        // 切回 LOCAL，避免影响其他用例
        mvc.perform(put("/api/v1/admin/config/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"provider\":\"LOCAL\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(45)
    void oidcRedirectBuildsEncodedAuthorizeUrl() throws Exception {
        // 回归：authorizeUrl() 曾用 UriComponentsBuilder.build(true)，scope 含空格、redirect_uri 含 "://"
        // 会被当作已编码组件校验失败 → 兜底 10001，授权页永远打不开。
        String location = mvc.perform(get("/api/v1/auth/oidc/redirect").param("redirect", "/admin"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertTrue(location != null, "必须 302 到 Casdoor 授权页");
        assertTrue(location.startsWith("https://casdoor.example.com/login/oauth/authorize?"),
                "授权地址应基于已配置的 endpoint：" + location);
        assertTrue(location.contains("client_id=bb-client"), "应带 client_id：" + location);
        assertTrue(location.contains("response_type=code"));
        // scope 中的空格必须编码成 %20 或 +，不能原样出现
        assertTrue(location.contains("scope=openid%20profile%20email%20phone")
                        || location.contains("scope=openid+profile+email+phone"),
                "scope 必须正确编码：" + location);
        assertTrue(location.contains("redirect_uri=http"), "redirect_uri 必须编码后带上：" + location);
        assertTrue(location.matches(".*state=[0-9a-f]{32}.*"), "应携带 32 位 hex state：" + location);
    }

    @Test
    @Order(5)
    void invalidSelectValueRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/config/orgsync")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"mode\":\"WHATEVER\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    @Order(6)
    void syncLogsEmptyButReadable() throws Exception {
        mvc.perform(get("/api/v1/org/sync/logs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(get("/api/v1/jobs/logs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(7)
    void syncWithoutPullModeIsNoop() throws Exception {
        String body = mvc.perform(post("/api/v1/org/sync")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String msg = objectMapper.readTree(body).path("data").asText("");
        assertFalse(msg.isEmpty());
        assertTrue(msg.contains("NONE"), "非 PULL 模式应提示当前模式：" + msg);
    }
}
