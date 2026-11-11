package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.dto.OrgPushPayload;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织同步 PUSH 模式（02 §3.3 模式 3）：私有 IDM 主动推送报文，本类负责幂等落库。
 *
 * <p>从 {@code OrgSyncService} 拆出（原类同时混着 PULL 编排 / PUSH / SCIM 三套逻辑）。
 * 处理顺序有依赖，不能随意调整：
 * <ol>
 *   <li>第一遍建部门（拿到 externalId → 本地 id 映射）</li>
 *   <li>第二遍回填父部门（子部门推送时父部门可能还没落库）</li>
 *   <li>再建用户（主部门依赖第一遍的映射）</li>
 *   <li>最后回填部门负责人（负责人引用的是用户）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgPushService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final OrgDirectorySupport support;

    @Transactional
    public void applyPush(OrgPushPayload payload) {
        support.requireMode("PUSH");
        Map<String, Long> deptIdByExternal = new HashMap<>();

        List<OrgPushPayload.OrgPushDept> depts = payload.departments() == null ? List.of() : payload.departments();
        for (OrgPushPayload.OrgPushDept d : depts) {
            Long id = upsertDept(d);
            deptIdByExternal.put(d.externalId(), id);
        }
        // 第二遍：父部门
        for (OrgPushPayload.OrgPushDept d : depts) {
            if (StringUtils.hasText(d.parentExternalId())) {
                Department row = departmentMapper.selectById(deptIdByExternal.get(d.externalId()));
                Long parentId = deptIdByExternal.get(d.parentExternalId());
                if (row != null && parentId != null && !parentId.equals(row.getId())) {
                    row.setParentId(parentId);
                    departmentMapper.updateById(row);
                }
            }
        }

        Map<String, Long> userIdByExternal = new HashMap<>();
        List<OrgPushPayload.OrgPushUser> users = payload.users() == null ? List.of() : payload.users();
        for (OrgPushPayload.OrgPushUser u : users) {
            Long id = upsertUser(u, deptIdByExternal);
            userIdByExternal.put(u.externalId(), id);
        }
        // 负责人回填
        for (OrgPushPayload.OrgPushDept d : depts) {
            if (StringUtils.hasText(d.leaderExternalId())) {
                Department row = departmentMapper.selectById(deptIdByExternal.get(d.externalId()));
                Long leaderId = userIdByExternal.get(d.leaderExternalId());
                if (row != null && leaderId != null) {
                    row.setLeaderUserId(leaderId);
                    departmentMapper.updateById(row);
                }
            }
        }
        // 关系表
        for (OrgPushPayload.OrgPushUser u : users) {
            Long uid = userIdByExternal.get(u.externalId());
            if (uid == null || u.deptExternalIds() == null) {
                continue;
            }
            for (String de : u.deptExternalIds()) {
                Long did = deptIdByExternal.get(de);
                if (did != null) {
                    support.linkUserDept(uid, did, de.equals(u.primaryDeptExternalId()));
                }
            }
        }

        if (payload.deleted() != null) {
            if (payload.deleted().userExternalIds() != null) {
                for (String ext : payload.deleted().userExternalIds()) {
                    SysUser u = support.findUserByExternalId(ext);
                    if (u != null) {
                        u.setStatus("DISABLED");
                        userMapper.updateById(u);
                    }
                }
            }
            if (payload.deleted().deptExternalIds() != null) {
                for (String ext : payload.deleted().deptExternalIds()) {
                    Department d = support.findDeptByCorpId(ext);
                    if (d != null) {
                        departmentMapper.deleteById(d.getId());
                    }
                }
            }
        }
        OrgSyncState.record(Instant.now(), "PUSH 接收成功：depts=" + depts.size() + " users=" + users.size());
        log.debug("PUSH 落库完成：depts={} users={}", depts.size(), users.size());
    }

    private Long upsertDept(OrgPushPayload.OrgPushDept d) {
        Department exist = support.findDeptByCorpId(d.externalId());
        if (exist == null) {
            Department row = new Department();
            row.setName(d.name());
            row.setCorpId(d.externalId());
            row.setSort(d.sort());
            row.setIsSystem(0);
            row.setCreatedAt(Instant.now());
            departmentMapper.insert(row);
            return row.getId();
        }
        exist.setName(d.name());
        if (d.sort() != null) {
            exist.setSort(d.sort());
        }
        exist.setUpdatedAt(Instant.now());
        departmentMapper.updateById(exist);
        return exist.getId();
    }

    private Long upsertUser(OrgPushPayload.OrgPushUser u, Map<String, Long> deptMap) {
        SysUser exist = support.findUserByExternalId(u.externalId());
        Long primaryDeptId = StringUtils.hasText(u.primaryDeptExternalId())
                ? deptMap.get(u.primaryDeptExternalId()) : null;
        if (exist == null) {
            SysUser row = new SysUser();
            row.setUsername(StringUtils.hasText(u.username()) ? u.username() : u.externalId());
            row.setExternalId(u.externalId());
            row.setName(StringUtils.hasText(u.name()) ? u.name() : u.externalId());
            row.setMobile(u.mobile());
            row.setEmail(u.email());
            row.setDeptId(primaryDeptId);
            row.setRoleCode("COMMON");
            row.setStatus(StringUtils.hasText(u.status()) ? u.status() : "ACTIVE");
            row.setMustChangePassword(0);
            userMapper.insert(row);
            return row.getId();
        }
        if (StringUtils.hasText(u.name())) {
            exist.setName(u.name());
        }
        exist.setMobile(u.mobile());
        exist.setEmail(u.email());
        exist.setDeptId(primaryDeptId);
        exist.setStatus(StringUtils.hasText(u.status()) ? u.status() : "ACTIVE");
        userMapper.updateById(exist);
        return exist.getId();
    }
}
