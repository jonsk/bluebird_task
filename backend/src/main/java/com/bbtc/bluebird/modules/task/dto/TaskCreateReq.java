package com.bbtc.bluebird.modules.task.dto;

import com.bbtc.bluebird.modules.task.domain.CycleRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 新建/更新任务请求（02 §4.5）。{@code version} 更新时必填。{@code parentId} 用于创建子任务。 */
public record TaskCreateReq(
        @NotBlank(message = "任务标题不能为空") String title,
        // 正文是纯文本（前端 textarea，不走 v-html），这里的上限只为挡住异常大写入；
        // 与工作日志的「可见文字 20000 字」额度对齐。
        @Size(max = 20000, message = "任务内容不能超过 20000 个字符") String content,
        Long parentId,
        Long version,
        Instant dueAt,
        Instant remindAt,
        String priority,
        CycleRule cycleRule,
        List<Long> assigneeIds,
        List<Long> ccIds,
        List<Long> tagIds,
        List<Long> fileIds) {

    public TaskCmd toCmd() {
        return new TaskCmd(title, content, parentId, dueAt, remindAt, priority, cycleRule,
                assigneeIds, ccIds, tagIds, fileIds, version);
    }
}
