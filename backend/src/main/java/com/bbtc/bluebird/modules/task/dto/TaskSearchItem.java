package com.bbtc.bluebird.modules.task.dto;

import java.time.Instant;

/** 任务引用选择器结果项（GET /tasks/search）。 */
public record TaskSearchItem(Long id, String title, String status, Instant dueAt) {
}
