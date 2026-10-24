package com.bbtc.bluebird.modules.note.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.note.application.NoteUserGrantService;
import com.bbtc.bluebird.modules.note.dto.NoteUserGrantReq;
import com.bbtc.bluebird.modules.note.dto.NoteUserGrantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户级全量授权接口。 */
@Tag(name = "note-grant")
@RestController
@RequestMapping("/api/v1/note-grants")
@RequiredArgsConstructor
public class NoteUserGrantController {

    private final NoteUserGrantService grantService;

    @Operation(summary = "发起授权")
    @OperateLog(module = "note", action = "grant-create")
    @PostMapping
    public ApiResult<NoteUserGrantVO> create(@RequestBody NoteUserGrantReq req) {
        return ApiResult.ok(grantService.create(req));
    }

    @Operation(summary = "授权列表（tab=created|granted|incoming|all）")
    @GetMapping
    public ApiResult<List<NoteUserGrantVO>> list(
            @RequestParam(defaultValue = "created") String tab,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String mode) {
        return ApiResult.ok(grantService.list(tab, status, role, mode));
    }

    @Operation(summary = "确认授权")
    @OperateLog(module = "note", action = "grant-approve")
    @PostMapping("/{id}/approve")
    public ApiResult<NoteUserGrantVO> approve(@PathVariable Long id) {
        return ApiResult.ok(grantService.approve(id));
    }

    @Operation(summary = "拒绝授权")
    @OperateLog(module = "note", action = "grant-reject")
    @PostMapping("/{id}/reject")
    public ApiResult<NoteUserGrantVO> reject(@PathVariable Long id) {
        return ApiResult.ok(grantService.reject(id));
    }

    @Operation(summary = "升级为 EDITOR")
    @OperateLog(module = "note", action = "grant-upgrade")
    @PostMapping("/{id}/upgrade")
    public ApiResult<NoteUserGrantVO> upgrade(@PathVariable Long id) {
        return ApiResult.ok(grantService.upgrade(id));
    }

    @Operation(summary = "撤销授权")
    @OperateLog(module = "note", action = "grant-revoke")
    @DeleteMapping("/{id}")
    public ApiResult<NoteUserGrantVO> revoke(@PathVariable Long id) {
        return ApiResult.ok(grantService.revoke(id));
    }
}
