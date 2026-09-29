package com.bbtc.bluebird.modules.audit.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.audit.application.LoginLogService;
import com.bbtc.bluebird.modules.audit.application.OperateLogService;
import com.bbtc.bluebird.modules.audit.dto.LoginLogVO;
import com.bbtc.bluebird.modules.audit.dto.OperateLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审计日志查询（02 §6.5）。ADMIN / AUDITOR 只读。 */
@Tag(name = "audit")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final OperateLogService operateLogService;
    private final LoginLogService loginLogService;

    @Operation(summary = "操作日志分页")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @GetMapping("/operates")
    public ApiResult<PageResult<OperateLogVO>> operates(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(operateLogService.page(page, size));
    }

    @Operation(summary = "登录日志分页")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @GetMapping("/logins")
    public ApiResult<PageResult<LoginLogVO>> logins(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(loginLogService.page(page, size));
    }
}
