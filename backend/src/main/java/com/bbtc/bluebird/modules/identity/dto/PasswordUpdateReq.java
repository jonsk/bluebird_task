package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码（PUT /users/{id}/password）。非首登须提供 oldPassword。
 */
public record PasswordUpdateReq(
        String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword) {
}
