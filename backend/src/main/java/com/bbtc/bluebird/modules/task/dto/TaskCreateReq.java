package com.bbtc.bluebird.modules.task.dto;

import com.bbtc.bluebird.modules.task.domain.CycleRule;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/** 新建/更新任务请求（02 §4.5）。{@code version} 更新时必填。 */
public record TaskCreateReq(
        @NotBlank String title,
        String content,
        Long version,
        Instant dueAt,
        Instant remindAt,
        String priority,
        CycleRule cycleRule,
        Long categoryId,
        List<Long> assigneeIds,
        List<Long> ccIds,
        List<Long> tagIds,
        List<Long> fileIds) {

    public TaskCmd toCmd() {
        return new TaskCmd(title, content, dueAt, remindAt, priority, cycleRule, categoryId,
                assigneeIds, ccIds, tagIds, fileIds, version);
    }
}
