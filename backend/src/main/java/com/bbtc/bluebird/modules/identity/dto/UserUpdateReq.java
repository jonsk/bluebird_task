package com.bbtc.bluebird.modules.identity.dto;

/** 更新用户（ADMIN，02 §2.5）。 */
public record UserUpdateReq(
        String name,
        Long deptId,
        String roleCode,
        String status) {
}
