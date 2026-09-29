package com.bbtc.bluebird.modules.org.dto;

import java.util.List;

/** 私有 IDM 组织推送报文（规范通用形态，02 §3.3 模式 3）。 */
public record OrgPushPayload(
        String type,
        String timestamp,
        List<OrgPushDept> departments,
        List<OrgPushUser> users,
        Deleted deleted) {

    public record OrgPushDept(
            String externalId,
            String name,
            String parentExternalId,
            String leaderExternalId,
            Integer sort) {
    }

    public record OrgPushUser(
            String externalId,
            String username,
            String name,
            String mobile,
            String email,
            String primaryDeptExternalId,
            List<String> deptExternalIds,
            String status) {
    }

    public record Deleted(
            List<String> userExternalIds,
            List<String> deptExternalIds) {
    }
}
