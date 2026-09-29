package com.bbtc.bluebird.modules.org.dto;

import java.time.Instant;

/** 组织同步状态（GET /org/sync/status）。 */
public record SyncStatusVO(
        boolean running,
        Instant lastSyncAt,
        String lastResult,
        String mode) {
}
