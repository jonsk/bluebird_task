package com.bbtc.bluebird.modules.org.dto;

import java.time.Instant;

/** 同步日志视图（后台日志页）。 */
public record OrgSyncLogVO(
        Long id,
        String source,
        String syncType,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Integer total,
        Integer inserted,
        Integer updated,
        Integer skipped,
        String errorMessage) {
}
