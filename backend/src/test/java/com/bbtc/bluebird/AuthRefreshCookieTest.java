package com.bbtc.bluebird;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * refresh token 迁 HttpOnly Cookie 的行为契约。
 *
 * <p>迁移的目标是「JS 拿不到长期凭证」，所以断言必须盯住三件事：
 * 响应体里不再出现 refreshToken、Cookie 带 HttpOnly/SameSite=Strict、刷新只认 Cookie 不认 body。
 * 这三条任一退化，本地测试都应该红。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456",
        "springdoc.api-docs.enabled=false"
})
@AutoConfigureMockMvc
class AuthRefreshCookieTest {

    private static final String LOGIN_BODY = "{\"username\":\"admin\",\"password\":\"Test123456\"}";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-test-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 登录下发HttpOnly刷新Cookie且响应体不含refreshToken() throws Exception {
        MockHttpServletResponse resp = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                // 关键：JSON 里不能再有 refreshToken，否则迁移没有意义
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String setCookie = String.join("\n", resp.getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains("bb_refresh="), "必须下发 bb_refresh Cookie");
        assertTrue(setCookie.toLowerCase().contains("httponly"), "refresh Cookie 必须 HttpOnly");
        assertTrue(setCookie.contains("SameSite=Strict"), "refresh Cookie 必须 SameSite=Strict");
        assertTrue(setCookie.contains("Path=/api/v1/auth"), "Cookie 只在认证端点发送");
        assertTrue(setCookie.contains("bb_session=1"), "需要非 HttpOnly 的会话标记供前端冷启动判断");
    }

    @Test
    void 刷新只认Cookie_请求体里的refresh无效() throws Exception {
        String refresh = loginAndGetRefreshCookie();

        // 带 Cookie：成功，且轮换出新 Cookie
        MockHttpServletResponse ok = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("bb_refresh", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse();
        assertTrue(String.join("\n", ok.getHeaders("Set-Cookie")).contains("bb_refresh="));

        // 只带请求体、不带 Cookie：必须拒绝（旧客户端不能靠 body 续命）
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20005));
    }

    @Test
    void 没有Cookie时刷新返回TOKEN_INVALID() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20005));
    }

    @Test
    void 登出清掉两个Cookie() throws Exception {
        String refresh = loginAndGetRefreshCookie();
        JsonNode root = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn().getResponse().getContentAsString());
        String access = root.path("data").path("accessToken").asText();

        MockHttpServletResponse resp = mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .cookie(new Cookie("bb_refresh", refresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        List<String> cookies = resp.getHeaders("Set-Cookie");
        String joined = String.join("\n", cookies);
        assertTrue(joined.contains("bb_refresh="), "登出要覆盖 bb_refresh");
        assertTrue(joined.contains("bb_session="), "登出要清掉会话标记");
        assertTrue(joined.contains("Max-Age=0"), "覆盖成过期即删除");
        assertFalse(cookies.isEmpty());
    }

    @Test
    void 已撤销的refresh不能再用() throws Exception {
        String refresh = loginAndGetRefreshCookie();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("bb_refresh", refresh)))
                .andExpect(jsonPath("$.code").value(0));

        // 同一份 refresh 再次使用：单活跃会话 + 轮换，旧值必须失效
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("bb_refresh", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20005));
    }

    /** 登录并从 Set-Cookie 里取出 refresh 值（服务端不对外返回，只能这样拿）。 */
    private String loginAndGetRefreshCookie() throws Exception {
        MockHttpServletResponse resp = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        for (String header : resp.getHeaders("Set-Cookie")) {
            if (header.startsWith("bb_refresh=")) {
                String value = header.substring("bb_refresh=".length());
                int semicolon = value.indexOf(';');
                assertNotNull(value);
                return semicolon > 0 ? value.substring(0, semicolon) : value;
            }
        }
        throw new AssertionError("登录响应里没有 bb_refresh Cookie：" + resp.getHeaders("Set-Cookie"));
    }

    @Test
    void 携带Cookie的刷新不会把refresh写回JSON() throws Exception {
        String refresh = loginAndGetRefreshCookie();
        String body = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("bb_refresh", refresh)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("refreshToken"), "响应体任何位置都不该出现 refreshToken");
        assertEquals(0, objectMapper.readTree(body).path("code").asInt());
    }
}
