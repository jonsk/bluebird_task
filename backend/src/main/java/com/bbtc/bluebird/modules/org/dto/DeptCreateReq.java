package com.bbtc.bluebird.modules.org.dto;

import jakarta.validation.constraints.NotBlank;

/** 新增部门（ADMIN）。 */
public record DeptCreateReq(
        @NotBlank String name,
        Long parentId,
        Long leaderId) {
}
