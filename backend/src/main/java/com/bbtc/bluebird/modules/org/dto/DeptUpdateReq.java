package com.bbtc.bluebird.modules.org.dto;

/** 修改部门（含 leaderId，ADMIN）。 */
public record DeptUpdateReq(
        String name,
        Long parentId,
        Long leaderId) {
}
