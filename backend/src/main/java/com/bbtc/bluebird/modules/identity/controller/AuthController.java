package com.bbtc.bluebird.modules.identity.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.IpUtils;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.application.AuthService;
import com.bbtc.bluebird.modules.identity.application.OidcSessionService;
import com.bbtc.bluebird.modules.identity.application.RefreshCookieService;
import com.bbtc.bluebird.modules.identity.dto.LoginReq;
import com.bbtc.bluebird.modules.identity.dto.TokenVO;
import com.bbtc.bluebird.modules.identity.oidc.CasdoorClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口（02 §2.5）。
 *
 * <p>provider 由配置中心（sys_config）决定，支持运行期切换：LOCAL 仅账密；
 * OIDC 展示 Casdoor 登录入口，账密仅 admin 可用。
 */
@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ConfigService config;
    private final CasdoorClient casdoorClient;
    private final OidcSessionService oidcSessionService;
    private final RefreshCookieService refreshCookie;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public ApiResult<TokenVO> login(@Valid @RequestBody LoginReq req, HttpServletRequest request,
                                    HttpServletResponse response) {
        String ip = IpUtils.clientIp(request);
        String ua = request.getHeader("User-Agent");
        TokenVO token = authService.login(req, ip, ua);
        refreshCookie.write(request, response, token.refreshToken());
        return ApiResult.ok(token.hideRefreshToken());
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(UserContext.currentUserId(), IpUtils.clientIp(request), request.getHeader("User-Agent"));
        refreshCookie.clear(request, response);
        return ApiResult.ok();
    }

    /**
     * 刷新 token：refresh 只从 HttpOnly Cookie 读，不再接受请求体。
     *
     * <p>Cookie 缺失/过期一律按 TOKEN_INVALID 处理（前端据此跳登录页）。
     * 这里顺带轮换 Cookie：每次刷新都下发新的 refresh，旧值在 {@code validateRefresh} 时被撤销。
     */
    @Operation(summary = "刷新 token（refresh 走 HttpOnly Cookie）")
    @PostMapping("/refresh")
    public ApiResult<TokenVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = refreshCookie.read(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        TokenVO token = authService.refresh(refresh);
        refreshCookie.write(request, response, token.refreshToken());
        return ApiResult.ok(token.hideRefreshToken());
    }

    @Operation(summary = "当前认证提供方配置")
    @GetMapping("/external/config")
    public ApiResult<Map<String, Object>> externalConfig(HttpServletRequest request) {
        String provider = config.provider();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", provider);
        if ("OIDC".equals(provider)) {
            body.put("authorizeUrl", "/api/v1/auth/oidc/redirect");
            body.put("displayName", "Casdoor");
            body.put("logoutUrl", casdoorClient.logoutUrl(origin(request) + "/login",
                    oidcSessionService.findIdToken(UserContext.currentUserId())));
        }
        return ApiResult.ok(body);
    }

    private String origin(HttpServletRequest request) {
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
        return scheme + "://" + host;
    }
}
