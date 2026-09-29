package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建用户（POST /users，ADMIN）。
 */
public record CreateUserCmd(
        @NotBlank String username,
        @NotBlank String name,
        String password,
        Long deptId,
        String roleCode) {
}
