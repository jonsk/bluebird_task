package com.bbtc.bluebird.modules.org.dto;

import jakarta.validation.constraints.NotBlank;

/** 新增部门（ADMIN）。 */
public record DeptCreateReq(
        @NotBlank(message = "部门名称不能为空") String name,
        Long parentId,
        Long leaderId) {
}
