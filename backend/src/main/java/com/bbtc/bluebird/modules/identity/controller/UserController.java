package com.bbtc.bluebird.modules.identity.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.identity.application.UserService;
import com.bbtc.bluebird.modules.identity.dto.CreateUserCmd;
import com.bbtc.bluebird.modules.identity.dto.PasswordUpdateReq;
import com.bbtc.bluebird.modules.identity.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（02 §2.5）。
 */
@Tag(name = "user")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResult<UserDTO> me() {
        return ApiResult.ok(userService.me());
    }

    @Operation(summary = "用户列表")
    @GetMapping
    public ApiResult<PageResult<UserDTO>> page(@RequestParam(required = false) Long deptId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(userService.page(deptId, keyword, page, size));
    }

    @Operation(summary = "新增用户（ADMIN）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody CreateUserCmd cmd) {
        return ApiResult.ok(userService.create(cmd));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/{id}/password")
    public ApiResult<Void> password(@PathVariable Long id, @Valid @RequestBody PasswordUpdateReq req) {
        userService.resetPassword(id, req);
        return ApiResult.ok();
    }
}
