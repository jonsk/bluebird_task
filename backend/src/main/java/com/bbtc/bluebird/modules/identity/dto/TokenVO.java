package com.bbtc.bluebird.modules.identity.dto;

/**
 * token 对（POST /auth/login|refresh 响应，02 §2.5）。
 */
public record TokenVO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean mustChangePassword,
        UserSummary user) {
}
