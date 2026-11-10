package com.bbtc.bluebird.modules.ai.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.ai.application.AiModelService;
import com.bbtc.bluebird.modules.ai.dto.AiModelSaveReq;
import com.bbtc.bluebird.modules.ai.dto.AiModelTestResult;
import com.bbtc.bluebird.modules.ai.dto.AiModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型配置（仅 ADMIN）。
 *
 * <p>支持配置多条模型，但全局最多一条处于启用（active）状态；API Key 只写不读，
 * 列表接口仅返回 {@code hasApiKey}。
 */
@Tag(name = "admin")
@RestController
@RequestMapping("/api/v1/admin/ai/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiModelController {

    private final AiModelService service;

    @Operation(summary = "模型列表")
    @GetMapping
    public ApiResult<List<AiModelVO>> list() {
        return ApiResult.ok(service.list());
    }

    @Operation(summary = "新增模型")
    @PostMapping
    public ApiResult<AiModelVO> create(@Valid @RequestBody AiModelSaveReq req) {
        return ApiResult.ok(service.create(req));
    }

    @Operation(summary = "编辑模型")
    @PutMapping("/{id}")
    public ApiResult<AiModelVO> update(@PathVariable Long id, @Valid @RequestBody AiModelSaveReq req) {
        return ApiResult.ok(service.update(id, req));
    }

    @Operation(summary = "删除模型（当前模型不可删）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "设为当前模型")
    @PostMapping("/{id}/activate")
    public ApiResult<AiModelVO> activate(@PathVariable Long id) {
        return ApiResult.ok(service.activate(id));
    }

    @Operation(summary = "连通性测试")
    @PostMapping("/{id}/test")
    public ApiResult<AiModelTestResult> test(@PathVariable Long id) {
        return ApiResult.ok(service.test(id));
    }
}
