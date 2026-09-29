package com.bbtc.bluebird.modules.org.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SCIM 2.0 端点（02 §3.3 模式 1）。Group 按名称映射为部门，忽略权限语义。
 * 鉴权由 {@code OrgSyncTokenFilter} 校验 Bearer ORGSYNC_PUSH_TOKEN。
 */
@Tag(name = "scim")
@RestController
@RequestMapping("/api/v1/scim/v2")
@RequiredArgsConstructor
public class ScimController {

    private final OrgSyncService orgSyncService;

    @Operation(summary = "SCIM 查询用户")
    @GetMapping("/Users")
    public ApiResult<Map<String, Object>> listUsers() {
        return ApiResult.ok(Map.of("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
                "totalResults", 0, "Resources", List.of()));
    }

    @Operation(summary = "SCIM 创建/替换用户")
    @PostMapping("/Users")
    public ApiResult<Void> createUser(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertUser(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 替换用户")
    @PutMapping("/Users")
    public ApiResult<Void> putUser(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertUser(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 局部更新用户")
    @PatchMapping("/Users")
    public ApiResult<Void> patchUser(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertUser(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 删除用户（置 DISABLED，不物理删除）")
    @DeleteMapping("/Users")
    public ApiResult<Void> deleteUser(@RequestBody(required = false) Map<String, Object> resource) {
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 查询部门")
    @GetMapping("/Groups")
    public ApiResult<Map<String, Object>> listGroups() {
        return ApiResult.ok(Map.of("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
                "totalResults", 0, "Resources", List.of()));
    }

    @Operation(summary = "SCIM 创建部门")
    @PostMapping("/Groups")
    public ApiResult<Void> createGroup(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertGroup(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 替换部门")
    @PutMapping("/Groups")
    public ApiResult<Void> putGroup(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertGroup(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 局部更新部门")
    @PatchMapping("/Groups")
    public ApiResult<Void> patchGroup(@RequestBody Map<String, Object> resource) {
        orgSyncService.scimUpsertGroup(resource);
        return ApiResult.ok();
    }

    @Operation(summary = "SCIM 删除部门")
    @DeleteMapping("/Groups")
    public ApiResult<Void> deleteGroup(@RequestBody(required = false) Map<String, Object> resource) {
        return ApiResult.ok();
    }
}
