package com.bbtc.bluebird.modules.task.dto;

import java.time.Instant;

/** 任务摘要（自定义栏条目内联）。 */
public record TaskBrief(Long id, String title, Boolean completed, Instant dueAt, String priority) {
}
