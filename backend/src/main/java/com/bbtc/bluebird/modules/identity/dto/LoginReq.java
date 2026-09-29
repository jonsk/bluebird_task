package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求（POST /auth/login）。
 */
public record LoginReq(
        @NotBlank String username,
        @NotBlank String password) {
}
