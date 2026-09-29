package com.bbtc.bluebird.modules.identity.dto;

/**
 * 当前用户视图（GET /users/me，02 §2.6）。
 */
public record UserDTO(
        Long id,
        String username,
        String name,
        String mobile,
        String email,
        String avatarUrl,
        Long deptId,
        String roleCode,
        String status) {
}
