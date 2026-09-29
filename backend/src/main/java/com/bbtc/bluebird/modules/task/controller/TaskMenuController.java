package com.bbtc.bluebird.modules.task.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.task.application.TaskMenuService;
import com.bbtc.bluebird.modules.task.dto.MenuCmd;
import com.bbtc.bluebird.modules.task.dto.MenuDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.List;

/** 自定义栏接口（02 §4.5）。 */
@Tag(name = "menu")
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class TaskMenuController {

    private final TaskMenuService menuService;

    public record MenuReq(@NotBlank String name, Integer sort) {
    }

    public record MenuItemReq(@NotNull Long taskId) {
    }

    @Operation(summary = "我的自定义栏")
    @GetMapping
    public ApiResult<List<MenuDTO>> list(@RequestParam(required = false) Long userId) {
        return ApiResult.ok(menuService.list(userId));
    }

    @Operation(summary = "新增栏")
    @RepeatSubmit
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody MenuReq req) {
        return ApiResult.ok(menuService.create(new MenuCmd(req.name(), req.sort())));
    }

    @Operation(summary = "改栏")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody MenuReq req) {
        menuService.update(id, new MenuCmd(req.name(), req.sort()));
        return ApiResult.ok();
    }

    @Operation(summary = "删栏")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "添加任务到栏")
    @PostMapping("/{id}/items")
    public ApiResult<Void> addItem(@PathVariable Long id, @Valid @RequestBody MenuItemReq req) {
        menuService.addItem(id, req.taskId());
        return ApiResult.ok();
    }

    @Operation(summary = "从栏移除任务")
    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResult<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        menuService.removeItem(itemId);
        return ApiResult.ok();
    }
}
