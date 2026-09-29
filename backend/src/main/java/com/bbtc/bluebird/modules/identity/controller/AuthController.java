package com.bbtc.bluebird.modules.identity.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.IpUtils;
import com.bbtc.bluebird.modules.identity.application.AuthService;
import com.bbtc.bluebird.modules.identity.dto.LoginReq;
import com.bbtc.bluebird.modules.identity.dto.RefreshReq;
import com.bbtc.bluebird.modules.identity.dto.TokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（02 §2.5）。
 */
@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.identity.provider:LOCAL}")
    private String provider;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public ApiResult<TokenVO> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        String ip = IpUtils.clientIp(request);
        String ua = request.getHeader("User-Agent");
        return ApiResult.ok(authService.login(req, ip, ua));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        authService.logout(UserContext.currentUserId());
        return ApiResult.ok();
    }

    @Operation(summary = "刷新 token")
    @PostMapping("/refresh")
    public ApiResult<TokenVO> refresh(@Valid @RequestBody RefreshReq req) {
        return ApiResult.ok(authService.refresh(req.refreshToken()));
    }

    @Operation(summary = "当前认证提供方配置")
    @GetMapping("/external/config")
    public ApiResult<Map<String, Object>> externalConfig() {
        return ApiResult.ok(Map.of("provider", provider));
    }
}
