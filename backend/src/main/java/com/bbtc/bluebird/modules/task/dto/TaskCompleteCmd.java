package com.bbtc.bluebird.modules.task.dto;

import java.time.Instant;

/** 完成/取消完成命令（ADR-013：须携带 version；周期任务带 dueAt）。 */
public record TaskCompleteCmd(Long version, Instant dueAt) {
}
