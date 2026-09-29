package com.bbtc.bluebird.modules.org.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import com.bbtc.bluebird.modules.org.dto.SyncStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 组织同步接口（02 §3.4，PULL 手动/状态）。 */
@Tag(name = "org")
@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgSyncController {

    private final OrgSyncService orgSyncService;

    @Operation(summary = "手动同步（PULL）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @PostMapping("/sync")
    public ApiResult<String> sync() {
        return ApiResult.ok(orgSyncService.sync());
    }

    @Operation(summary = "同步状态")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @GetMapping("/sync/status")
    public ApiResult<SyncStatusVO> status() {
        return ApiResult.ok(orgSyncService.status());
    }
}
