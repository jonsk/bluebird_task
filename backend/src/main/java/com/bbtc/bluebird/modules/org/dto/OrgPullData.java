package com.bbtc.bluebird.modules.org.dto;

import java.util.List;

/**
 * 一次 PULL 拉取的结果集。
 *
 * @param departments 部门（先父后子由调用方排序/两遍处理）
 * @param users       用户（含角色与部门归属）
 */
public record OrgPullData(
        String source,
        List<OrgDept> departments,
        List<OrgDept.OrgUser> users) {
}
