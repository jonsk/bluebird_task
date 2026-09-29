package com.bbtc.bluebird.modules.org.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import com.bbtc.bluebird.modules.org.dto.OrgPushPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私有 IDM 组织推送（02 §3.3 模式 3）。鉴权由 {@code OrgSyncTokenFilter} 校验 Bearer ORGSYNC_PUSH_TOKEN。
 */
@Tag(name = "org")
@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgPushController {

    private final OrgSyncService orgSyncService;

    @Operation(summary = "私有 IDM 推送")
    @PostMapping("/push")
    public ApiResult<Void> push(@RequestBody OrgPushPayload payload) {
        orgSyncService.applyPush(payload);
        return ApiResult.ok();
    }
}
