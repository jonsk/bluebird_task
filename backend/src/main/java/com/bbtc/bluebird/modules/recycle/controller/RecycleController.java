package com.bbtc.bluebird.modules.recycle.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.recycle.application.RecycleService;
import com.bbtc.bluebird.modules.recycle.dto.RecycleBatchReq;
import com.bbtc.bluebird.modules.recycle.dto.RecycleBatchResult;
import com.bbtc.bluebird.modules.recycle.dto.RecycleItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站接口（2026-10-30）。
 *
 * <p>列表/恢复：登录用户，且只能看到**自己删除**的记录（服务层按 deleted_by 收窄）。
 * 彻底删除与清空：仅 ADMIN（{@code @PreAuthorize}）。
 */
@Tag(name = "recycle")
@RestController
@RequestMapping("/api/v1/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    @Operation(summary = "回收站列表（type=all|task|note，仅自己删除的）")
    @GetMapping
    public ApiResult<PageResult<RecycleItemVO>> page(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(recycleService.page(type, keyword, page, size));
    }

    @Operation(summary = "恢复任务（含子树与仍存在的祖先链）")
    @OperateLog(module = "recycle", action = "restore-task")
    @PostMapping("/tasks/{id}/restore")
    public ApiResult<Void> restoreTask(@PathVariable Long id) {
        recycleService.restoreTask(id);
        return ApiResult.ok();
    }

    @Operation(summary = "恢复工作日志（附件与引用关系随之回来）")
    @OperateLog(module = "recycle", action = "restore-note")
    @PostMapping("/notes/{id}/restore")
    public ApiResult<Void> restoreNote(@PathVariable Long id) {
        recycleService.restoreNote(id);
        return ApiResult.ok();
    }

    @Operation(summary = "批量恢复")
    @RepeatSubmit
    @OperateLog(module = "recycle", action = "restore-batch")
    @PostMapping("/restore")
    public ApiResult<RecycleBatchResult> restore(@Valid @RequestBody RecycleBatchReq req) {
        return ApiResult.ok(recycleService.restore(req.items()));
    }

    @Operation(summary = "彻底删除任务（仅管理员，不可恢复）")
    @PreAuthorize("hasRole('ADMIN')")
    @OperateLog(module = "recycle", action = "purge-task")
    @DeleteMapping("/tasks/{id}")
    public ApiResult<Void> purgeTask(@PathVariable Long id) {
        recycleService.purgeTask(id);
        return ApiResult.ok();
    }

    @Operation(summary = "彻底删除工作日志（仅管理员，不可恢复）")
    @PreAuthorize("hasRole('ADMIN')")
    @OperateLog(module = "recycle", action = "purge-note")
    @DeleteMapping("/notes/{id}")
    public ApiResult<Void> purgeNote(@PathVariable Long id) {
        recycleService.purgeNote(id);
        return ApiResult.ok();
    }

    @Operation(summary = "批量彻底删除（仅管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @RepeatSubmit
    @OperateLog(module = "recycle", action = "purge-batch")
    @PostMapping("/purge")
    public ApiResult<RecycleBatchResult> purge(@Valid @RequestBody RecycleBatchReq req) {
        return ApiResult.ok(recycleService.purge(req.items()));
    }

    @Operation(summary = "清空回收站（仅管理员，type=all|task|note）")
    @PreAuthorize("hasRole('ADMIN')")
    @RepeatSubmit
    @OperateLog(module = "recycle", action = "empty")
    @DeleteMapping("/empty")
    public ApiResult<Integer> empty(@RequestParam(defaultValue = "all") String type) {
        return ApiResult.ok(recycleService.empty(type));
    }
}
