package com.bbtc.bluebird.modules.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * refresh token 白名单（替代 Redis，ADR-016，02 §1.9.1）。
 */
@Data
@TableName("sys_refresh_token")
public class SysRefreshToken {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String tokenHash;
    private Instant issuedAt;
    private Instant expiresAt;
    private Integer revoked;
    private Instant lastSeenAt;
}
