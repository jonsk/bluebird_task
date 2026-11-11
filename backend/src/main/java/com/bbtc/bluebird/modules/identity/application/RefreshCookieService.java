package com.bbtc.bluebird.modules.identity.application;

import com.bbtc.bluebird.config.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * refresh token 的 Cookie 载体（HttpOnly，JS 读不到）。
 *
 * <p>为什么必须这么做：refresh token 存在 localStorage 时，任意 XSS 都能长期冒用会话；
 * access token 已内存化、CSP 禁内联脚本、富文本双端消毒，但那些只降低 XSS 概率，
 * 不改变「脚本能读到长期凭证」这个事实。
 *
 * <p>几处刻意的取值（改动前先想清楚）：
 * <ul>
 *   <li>{@code HttpOnly}：JS 完全不可见，杜绝 XSS 直接窃取；</li>
 *   <li>{@code Path=/api/v1/auth}：只在登录/刷新/登出三个端点上发送，其它请求白带一个长期凭证没有意义；</li>
 *   <li>{@code SameSite=Strict}：项目无 CSRF token（无状态 JWT），刷新是 POST + Cookie，
 *       Strict 让跨站发起时浏览器根本不带 Cookie，是这里唯一可用且够用的 CSRF 防线；</li>
 *   <li>{@code Secure}：按请求实际协议判断（反代下看 {@code X-Forwarded-Proto}），
 *       HTTP 部署时加 Secure 会让浏览器直接丢弃 Cookie，反而登不上。</li>
 * </ul>
 *
 * <p>另发一个非 HttpOnly 的 {@link #SESSION_MARKER}（值为 1），仅供前端判断
 * 「大概登录过」，避免每次冷启动都打一次必然失败的 /auth/refresh。它不是凭证，读走无意义。
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieService {

    public static final String REFRESH_COOKIE = "bb_refresh";
    public static final String SESSION_MARKER = "bb_session";
    /** 与控制器路径一致：只在该前缀下发送。 */
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    /** 登录/刷新成功后写 Cookie（刷新时轮换，旧值由后续 validateRefresh 再校验）。 */
    public void write(HttpServletRequest request, HttpServletResponse response, String refreshToken) {
        long ttl = jwtProperties.getRefreshTtl();
        boolean secure = isSecure(request);
        add(response, ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(ttl)
                .build());
        add(response, ResponseCookie.from(SESSION_MARKER, "1")
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(ttl)
                .build());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** 登出：同名同 Path 覆盖成 Max-Age=0。 */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = isSecure(request);
        add(response, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secure).sameSite("Strict").path(REFRESH_PATH).maxAge(0).build());
        add(response, ResponseCookie.from(SESSION_MARKER, "")
                .httpOnly(false).secure(secure).sameSite("Strict").path("/").maxAge(0).build());
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean isSecure(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return "https".equalsIgnoreCase(forwarded.split(",")[0].trim());
        }
        return request.isSecure();
    }
}
