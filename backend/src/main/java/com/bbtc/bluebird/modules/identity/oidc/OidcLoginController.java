package com.bbtc.bluebird.modules.identity.oidc;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.util.IpUtils;
import com.bbtc.bluebird.modules.audit.application.LoginLogService;
import com.bbtc.bluebird.modules.identity.application.TokenService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.dto.TokenVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Casdoor OIDC 登录（授权码流）。
 *
 * <p>流程：{@code /redirect} 302 到 Casdoor 授权页 → Casdoor 回调 {@code /callback} →
 * 换 token → 拉 userinfo → 本地用户即时 upsert → 签发**自有 JWT** →
 * 302 到前端 {@code /oauth/success#access_token=..&refresh_token=..}（fragment 不进服务端日志）。
 *
 * <p>路径位于 {@code /api/v1/auth/**} 白名单内，无需登录态。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oidc")
@RequiredArgsConstructor
public class OidcLoginController {

    private static final String STATE_COOKIE = "bb_oidc_state";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CasdoorClient casdoorClient;
    private final CasdoorUserService casdoorUserService;
    private final TokenService tokenService;
    private final LoginLogService loginLogService;

    @GetMapping("/redirect")
    public void redirect(@RequestParam(required = false) String redirect,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (!casdoorClient.configured()) {
            fail(response, "Casdoor 尚未配置完整（地址 / Client ID / Secret）");
            return;
        }
        String state = HexFormat.of().formatHex(randomBytes(16));
        String target = normalizeTarget(redirect);
        String payload = state + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(target.getBytes(StandardCharsets.UTF_8));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(STATE_COOKIE, payload)
                .httpOnly(true).path("/").maxAge(Duration.ofMinutes(10)).sameSite("Lax").build().toString());
        try {
            response.sendRedirect(casdoorClient.authorizeUrl(state, redirectUri(request)));
        } catch (Exception e) {
            // 配置残缺（地址非法、Client ID 含非法字符等）不该把用户丢到一个 JSON 错误码页面
            log.warn("拼接 Casdoor 授权地址失败：{}", e.getMessage());
            fail(response, "Casdoor 授权地址不可用，请检查「服务地址 / Client ID」配置");
        }
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         @CookieValue(name = STATE_COOKIE, required = false) String cookie,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        clearStateCookie(response);
        if (error != null && !error.isBlank()) {
            fail(response, "Casdoor 返回错误：" + error);
            return;
        }
        if (code == null || code.isBlank()) {
            fail(response, "缺少授权码");
            return;
        }
        String[] parsed = parseStateCookie(cookie);
        if (parsed == null || state == null || !parsed[0].equals(state)) {
            fail(response, "登录状态校验失败，请重试");
            return;
        }
        String target = parsed[1];
        String ip = IpUtils.clientIp(request);
        String ua = request.getHeader("User-Agent");
        try {
            Map<String, Object> token = casdoorClient.exchangeCode(code, redirectUri(request));
            String accessToken = String.valueOf(token.get("access_token"));
            Map<String, Object> info = casdoorClient.userInfo(accessToken);
            SysUser user = casdoorUserService.upsert(info);
            if (!"ACTIVE".equals(user.getStatus())) {
                loginLogService.record(user.getUsername(), false, ip, ua, "CASDOOR", "LOGIN", user.getExternalId());
                fail(response, "账号已被禁用");
                return;
            }
            TokenVO issued = tokenService.issue(user);
            loginLogService.record(user.getUsername(), true, ip, ua, "CASDOOR", "LOGIN", user.getExternalId());
            response.sendRedirect("/oauth/success#access_token=" + enc(issued.accessToken())
                    + "&refresh_token=" + enc(issued.refreshToken())
                    + "&redirect=" + enc(target));
        } catch (BusinessException e) {
            log.warn("Casdoor 登录失败：{}", e.getMessage());
            loginLogService.record(null, false, ip, ua, "CASDOOR", "LOGIN", null);
            fail(response, e.getMessage());
        } catch (Exception e) {
            log.error("Casdoor 登录异常", e);
            loginLogService.record(null, false, ip, ua, "CASDOOR", "LOGIN", null);
            fail(response, "登录失败，请稍后重试");
        }
    }

    // ==================== helpers ====================

    /** 回调地址：优先取配置，否则按当前请求推导（兼容反代）。 */
    private String redirectUri(HttpServletRequest request) {
        String configured = casdoorClient.redirectUri();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        return scheme + "://" + host + "/api/v1/auth/oidc/callback";
    }

    private void fail(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect("/oauth/success#error=" + enc(message));
    }

    private String normalizeTarget(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/index";
        }
        return redirect;
    }

    private String[] parseStateCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        int dot = cookie.indexOf('.');
        if (dot <= 0 || dot == cookie.length() - 1) {
            return null;
        }
        try {
            String target = new String(Base64.getUrlDecoder().decode(cookie.substring(dot + 1)), StandardCharsets.UTF_8);
            return new String[]{cookie.substring(0, dot), normalizeTarget(target)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void clearStateCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(STATE_COOKIE, "")
                .httpOnly(true).path("/").maxAge(Duration.ZERO).build().toString());
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
