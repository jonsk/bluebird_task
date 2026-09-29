package com.bbtc.bluebird.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码（PUT /users/{id}/password）。非首登须提供 oldPassword。
 */
public record PasswordUpdateReq(
        String oldPassword,
        @NotBlank String newPassword) {
}
