package com.bbtc.bluebird.modules.task.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.task.application.CategoryService;
import com.bbtc.bluebird.modules.task.dto.CategoryCmd;
import com.bbtc.bluebird.modules.task.dto.CategoryNodeDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/** 分类接口（ADR-015，02 §4.5）。 */
@Tag(name = "category")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    public record CategoryReq(@NotBlank String name, Long parentId, String scope, Long deptId) {
        CategoryCmd toCmd() {
            return new CategoryCmd(name, parentId, scope, deptId);
        }
    }

    @Operation(summary = "可见分类树")
    @GetMapping
    public ApiResult<List<CategoryNodeDTO>> tree(@RequestParam(required = false) String scope) {
        return ApiResult.ok(categoryService.tree(scope));
    }

    @Operation(summary = "新增分类")
    @RepeatSubmit
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody CategoryReq req) {
        return ApiResult.ok(categoryService.create(req.toCmd()));
    }

    @Operation(summary = "改分类")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryReq req) {
        categoryService.update(id, req.toCmd());
        return ApiResult.ok();
    }

    @Operation(summary = "删分类")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResult.ok();
    }
}
