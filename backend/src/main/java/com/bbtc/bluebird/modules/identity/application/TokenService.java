package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.config.security.JwtProperties;
import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.identity.domain.SysRefreshToken;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.dto.TokenVO;
import com.bbtc.bluebird.modules.identity.dto.UserSummary;
import com.bbtc.bluebird.modules.identity.infrastructure.SysRefreshTokenMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * token 签发 / 刷新 / 失效（02 §1.9.1/§2.3）。refresh 白名单落库，单活跃会话。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final SysRefreshTokenMapper refreshTokenMapper;

    @Transactional
    public TokenVO issue(SysUser user) {
        String access = jwtUtils.createAccess(user.getId(), user.getUsername(), user.getRoleCode());
        String refresh = jwtUtils.createRefresh(user.getId(), user.getUsername(), user.getRoleCode());
        // 单活跃会话：撤销该用户既有未撤销 refresh
        refreshTokenMapper.update(null, Wrappers.<SysRefreshToken>lambdaUpdate()
                .eq(SysRefreshToken::getUserId, user.getId())
                .eq(SysRefreshToken::getRevoked, 0)
                .set(SysRefreshToken::getRevoked, 1));

        SysRefreshToken row = new SysRefreshToken();
        row.setUserId(user.getId());
        row.setTokenHash(sha256(refresh));
        Instant now = Instant.now();
        row.setIssuedAt(now);
        row.setExpiresAt(now.plusSeconds(jwtProperties.getRefreshTtl()));
        row.setRevoked(0);
        row.setLastSeenAt(now);
        refreshTokenMapper.insert(row);

        UserSummary summary = new UserSummary(user.getId(), user.getUsername(), user.getName(),
                user.getDeptId(), user.getRoleCode(), user.getAvatarUrl());
        boolean mustChange = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
        return new TokenVO(access, refresh, jwtUtils.accessTtl(), mustChange, summary);
    }

    @Transactional
    public Long validateRefresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtils.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!JwtUtils.TYPE_REFRESH.equals(claims.get(JwtUtils.CLAIM_TYPE, String.class))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        SysRefreshToken row = refreshTokenMapper.selectOne(Wrappers.<SysRefreshToken>lambdaQuery()
                .eq(SysRefreshToken::getTokenHash, sha256(refreshToken))
                .eq(SysRefreshToken::getRevoked, 0)
                .last("limit 1"));
        if (row == null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        row.setRevoked(1);
        refreshTokenMapper.updateById(row);
        return userId;
    }

    @Transactional
    public void revoke(Long userId) {
        refreshTokenMapper.update(null, Wrappers.<SysRefreshToken>lambdaUpdate()
                .eq(SysRefreshToken::getUserId, userId)
                .eq(SysRefreshToken::getRevoked, 0)
                .set(SysRefreshToken::getRevoked, 1));
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
