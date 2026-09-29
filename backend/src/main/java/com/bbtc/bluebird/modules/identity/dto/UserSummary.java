package com.bbtc.bluebird.modules.identity.dto;

/**
 * 用户摘要（登录响应内联，02 §2.5）。
 */
public record UserSummary(
        Long id,
        String username,
        String name,
        Long deptId,
        String roleCode,
        String avatarUrl) {
}
