package com.bbtc.bluebird.modules.config.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.config.application.CasdoorConnectivityTester;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.config.dto.ConfigGroupVO;
import com.bbtc.bluebird.modules.config.dto.ConfigSaveReq;
import com.bbtc.bluebird.modules.config.dto.ConfigTestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台配置接口（仅 ADMIN）。
 *
 * <p>密文（client-secret）读取时只返回掩码，写入时传掩码表示不修改。
 */
@Tag(name = "admin")
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final ConfigService configService;
    private final CasdoorConnectivityTester casdoorTester;

    @Operation(summary = "全部配置分组")
    @GetMapping
    public ApiResult<List<ConfigGroupVO>> all() {
        return ApiResult.ok(configService.list());
    }

    @Operation(summary = "单个配置分组")
    @GetMapping("/{group}")
    public ApiResult<ConfigGroupVO> group(@PathVariable String group) {
        return ApiResult.ok(configService.group(group));
    }

    @Operation(summary = "保存配置分组")
    @PutMapping("/{group}")
    public ApiResult<ConfigGroupVO> save(@PathVariable String group, @RequestBody ConfigSaveReq req) {
        return ApiResult.ok(configService.save(group, req.values(), UserContext.currentUserId()));
    }

    @Operation(summary = "测试 Casdoor 连通性")
    @PostMapping("/casdoor/test")
    public ApiResult<ConfigTestResult> testCasdoor() {
        return ApiResult.ok(casdoorTester.test());
    }
}
