package com.bbtc.bluebird.modules.job.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.job.application.FieldCleanupJob;
import com.bbtc.bluebird.modules.job.application.JobLogService;
import com.bbtc.bluebird.modules.job.application.OrgSyncJob;
import com.bbtc.bluebird.modules.job.dto.JobLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 调度任务手动重跑（0306/Q6）。 */
@Tag(name = "job")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final FieldCleanupJob fieldCleanupJob;
    private final OrgSyncJob orgSyncJob;
    private final JobLogService jobLogService;

    @Operation(summary = "手动重跑调度任务（ADMIN）")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{name}/run")
    public ApiResult<String> run(@PathVariable String name) {
        long start = System.currentTimeMillis();
        try {
            String msg = switch (name) {
                case "field-cleanup" -> {
                    int removed = fieldCleanupJob.run();
                    yield "删除孤儿文件 " + removed + " 个";
                }
                case "org-sync" -> {
                    orgSyncJob.run();
                    yield "已触发";
                }
                default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "未知任务：" + name);
            };
            jobLogService.record(name, start, true, msg);
            return ApiResult.ok(msg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            jobLogService.record(name, start, false, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务执行失败：" + e.getMessage());
        }
    }

    @Operation(summary = "调度任务日志分页（ADMIN）")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/logs")
    public ApiResult<PageResult<JobLogVO>> logs(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(jobLogService.page(page, size));
    }
}
