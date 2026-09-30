package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新建用户（POST /users，ADMIN）。
 *
 * <p>{@code deptId} 为必填（用户反馈 #5：新建用户必须选择一个部门）。
 */
public record CreateUserCmd(
        @NotBlank String username,
        @NotBlank String name,
        String password,
        @NotNull(message = "请选择部门") Long deptId,
        String roleCode,
        String mobile,
        String email) {
}
