package com.bbtc.bluebird.modules.identity.dto;

/**
 * 更新用户（ADMIN，02 §2.5）。
 *
 * <p>{@code mobile}/{@code email} 传空串表示清空（前端以空串表达「清空该字段」）。
 */
public record UserUpdateReq(
        String name,
        Long deptId,
        String roleCode,
        String status,
        String mobile,
        String email) {
}
