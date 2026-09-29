package com.bbtc.bluebird.modules.task.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 完成/取消完成请求（ADR-013：version 必填）。 */
public record TaskCompleteReq(@NotNull Long version, Instant dueAt) {
}
