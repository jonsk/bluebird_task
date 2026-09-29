package com.bbtc.bluebird.modules.audit.dto;

import java.time.Instant;

/** 操作日志视图（02 §6.2）。 */
public record OperateLogVO(
        Long id,
        String module,
        String action,
        String uri,
        String method,
        Long userId,
        String ip,
        String userAgent,
        Long duration,
        String status,
        String msg,
        Instant createdAt) {
}
