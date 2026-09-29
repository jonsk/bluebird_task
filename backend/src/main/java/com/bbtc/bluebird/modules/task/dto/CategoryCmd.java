package com.bbtc.bluebird.modules.task.dto;

/** 分类命令（新增/修改）。 */
public record CategoryCmd(String name, Long parentId, String scope, Long deptId) {
}
