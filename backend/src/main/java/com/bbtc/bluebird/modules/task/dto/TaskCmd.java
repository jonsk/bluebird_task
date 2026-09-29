package com.bbtc.bluebird.modules.task.dto;

import com.bbtc.bluebird.modules.task.domain.CycleRule;

import java.time.Instant;
import java.util.List;

/** 新建/更新任务命令（02 §4.5）。 */
public record TaskCmd(
        String title,
        String content,
        Instant dueAt,
        Instant remindAt,
        String priority,
        CycleRule cycleRule,
        Long categoryId,
        List<Long> assigneeIds,
        List<Long> ccIds,
        List<Long> tagIds,
        List<Long> fileIds,
        Long version) {
}
