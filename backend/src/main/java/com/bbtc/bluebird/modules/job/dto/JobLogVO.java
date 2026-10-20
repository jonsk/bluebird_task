package com.bbtc.bluebird.modules.job.dto;

import java.time.Instant;

/** 调度任务日志视图。 */
public record JobLogVO(
        Long id,
        String job,
        boolean success,
        Long duration,
        String message,
        Instant startedAt,
        Instant finishedAt) {
}
