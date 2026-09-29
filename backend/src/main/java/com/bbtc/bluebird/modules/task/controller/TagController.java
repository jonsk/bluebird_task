package com.bbtc.bluebird.modules.task.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.task.application.TagService;
import com.bbtc.bluebird.modules.task.dto.TagVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 标签接口（02 §4.5）。 */
@Tag(name = "tag")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 标签请求体。 */
    public record TagReq(@NotBlank String name) {
    }

    @Operation(summary = "我的标签")
    @GetMapping
    public ApiResult<List<TagVO>> list() {
        return ApiResult.ok(tagService.list());
    }

    @Operation(summary = "新增标签")
    @RepeatSubmit
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody TagReq req) {
        return ApiResult.ok(tagService.create(req.name()));
    }

    @Operation(summary = "改标签")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody TagReq req) {
        tagService.update(id, req.name());
        return ApiResult.ok();
    }

    @Operation(summary = "删标签")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResult.ok();
    }
}
