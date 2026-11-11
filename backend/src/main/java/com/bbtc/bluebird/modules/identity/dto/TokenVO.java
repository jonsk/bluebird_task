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

    /**
     * 对外响应时抹掉 refreshToken：它走 HttpOnly Cookie 下发，不应出现在 JSON 里
     * （否则等于把凭证又还给 JS，迁移就白做了）。
     */
    public TokenVO hideRefreshToken() {
        return new TokenVO(accessToken, null, expiresIn, mustChangePassword, user);
    }
}
