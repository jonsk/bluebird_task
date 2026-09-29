package com.bbtc.bluebird.modules.identity.dto;

/**
 * 当前用户视图（GET /users/me、GET /users，02 §2.6）。含 deptName（UserVO 契约）。
 */
public record UserDTO(
        Long id,
        String username,
        String name,
        String mobile,
        String email,
        String avatarUrl,
        Long deptId,
        String deptName,
        String roleCode,
        String status) {
}
