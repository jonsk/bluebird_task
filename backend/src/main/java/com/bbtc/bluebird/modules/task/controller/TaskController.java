package com.bbtc.bluebird.modules.task.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.task.application.TaskApplicationService;
import com.bbtc.bluebird.modules.task.application.TaskQueryService;
import com.bbtc.bluebird.modules.task.dto.CountVO;
import com.bbtc.bluebird.modules.task.dto.TaskCompleteReq;
import com.bbtc.bluebird.modules.task.dto.TaskCreateReq;
import com.bbtc.bluebird.modules.task.dto.TaskDetailVO;
import com.bbtc.bluebird.modules.task.dto.TaskQuery;
import com.bbtc.bluebird.modules.task.dto.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 任务接口（02 §4.5）。 */
@Tag(name = "task")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskApplicationService taskApp;
    private final TaskQueryService taskQuery;

    @Operation(summary = "任务列表（六大视图）")
    @GetMapping
    public ApiResult<PageResult<TaskVO>> page(@RequestParam String scope,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "false") boolean subordinate,
                                              @RequestParam(required = false) String date,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(taskQuery.page(new TaskQuery(scope, keyword, subordinate, date, page, size)));
    }

    @Operation(summary = "各视图计数")
    @GetMapping("/count")
    public ApiResult<CountVO> count() {
        return ApiResult.ok(taskQuery.counts());
    }

    @Operation(summary = "日历区间")
    @GetMapping("/calendar")
    public ApiResult<PageResult<TaskVO>> calendar(@RequestParam Instant start, @RequestParam Instant end) {
        List<TaskVO> list = taskQuery.calendar(start, end);
        return ApiResult.ok(PageResult.of(list, list.size(), 1, list.size()));
    }

    @Operation(summary = "子任务列表")
    @GetMapping("/subtasks")
    public ApiResult<PageResult<TaskVO>> subtasks(@RequestParam Long parentId) {
        List<TaskVO> list = taskQuery.subtasks(parentId);
        return ApiResult.ok(PageResult.of(list, list.size(), 1, list.size()));
    }

    @Operation(summary = "新建主任务")
    @RepeatSubmit
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody TaskCreateReq req) {
        return ApiResult.ok(taskApp.create(req.toCmd()));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public ApiResult<TaskDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(taskQuery.detail(id));
    }

    @Operation(summary = "更新任务（须携带 version）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody TaskCreateReq req) {
        taskApp.update(id, req.toCmd());
        return ApiResult.ok();
    }

    @Operation(summary = "软删除（不可逆）")
    @com.bbtc.bluebird.modules.audit.annotation.OperateLog(module = "task", action = "delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskApp.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "完成任务")
    @com.bbtc.bluebird.modules.audit.annotation.OperateLog(module = "task", action = "complete")
    @PostMapping("/{id}/complete")
    public ApiResult<Void> complete(@PathVariable Long id, @Valid @RequestBody TaskCompleteReq req) {
        taskApp.complete(id, new com.bbtc.bluebird.modules.task.dto.TaskCompleteCmd(req.version(), req.dueAt()));
        return ApiResult.ok();
    }

    @Operation(summary = "取消完成")
    @com.bbtc.bluebird.modules.audit.annotation.OperateLog(module = "task", action = "uncomplete")
    @PostMapping("/{id}/uncomplete")
    public ApiResult<Void> uncomplete(@PathVariable Long id, @Valid @RequestBody TaskCompleteReq req) {
        taskApp.uncomplete(id, new com.bbtc.bluebird.modules.task.dto.TaskCompleteCmd(req.version(), req.dueAt()));
        return ApiResult.ok();
    }

    @Operation(summary = "收藏")
    @PostMapping("/{id}/collect")
    public ApiResult<Void> collect(@PathVariable Long id) {
        taskApp.collect(id);
        return ApiResult.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}/collect")
    public ApiResult<Void> uncollect(@PathVariable Long id) {
        taskApp.uncollect(id);
        return ApiResult.ok();
    }
}
