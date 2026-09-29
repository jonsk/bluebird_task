package com.bbtc.bluebird.modules.identity.domain;

/**
 * 角色枚举（无角色表，ADR-003；四值 0306/Q1）。{@code ADMIN/AUDITOR/USER_MANAGER/COMMON}。
 */
public enum RoleCode {
    ADMIN,
    AUDITOR,
    USER_MANAGER,
    COMMON
}
