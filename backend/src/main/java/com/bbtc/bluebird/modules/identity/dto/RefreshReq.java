package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 token 请求（POST /auth/refresh）。
 */
public record RefreshReq(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
