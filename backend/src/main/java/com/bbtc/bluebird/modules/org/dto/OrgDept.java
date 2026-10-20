package com.bbtc.bluebird.modules.org.dto;

import java.util.List;

/**
 * PULL 同步的中间模型（与具体 IdP 解耦）。
 *
 * @param externalId       外部唯一标识（落 {@code sys_department.corp_id}）
 * @param parentExternalId 父节点外部标识（可空）
 */
public record OrgDept(
        String externalId,
        String parentExternalId,
        String name,
        Integer sort) {

    /** 待落库的用户。 */
    public record OrgUser(
            String externalId,
            String username,
            String name,
            String mobile,
            String email,
            String avatar,
            String status,
            String roleCode,
            List<String> deptExternalIds,
            String primaryDeptExternalId) {
    }
}
