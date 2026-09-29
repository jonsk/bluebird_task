package com.bbtc.bluebird.modules.audit.dto;

import java.time.Instant;

/** 登录日志视图（02 §6.2）。 */
public record LoginLogVO(
        Long id,
        String username,
        boolean success,
        String ip,
        String userAgent,
        Instant createdAt) {
}
