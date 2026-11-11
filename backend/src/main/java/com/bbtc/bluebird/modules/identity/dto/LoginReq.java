package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求（POST /auth/login）。
 */
public record LoginReq(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}
