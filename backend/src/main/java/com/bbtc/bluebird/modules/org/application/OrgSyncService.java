package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.dto.OrgPushPayload;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.dto.SyncStatusVO;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 组织同步（02 §3.3，四模式）。单实例进程内锁（ADR-016），幂等 upsert，不删实体仅置 DISABLED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    private final ReentrantLock lock = new ReentrantLock();

    @Value("${app.orgsync.mode:NONE}")
    private String mode;

    public String mode() {
        return mode;
    }

    /** PULL 模式手动/定时同步。无 Adapter 时为空操作。 */
    public String sync() {
        if (!"PULL".equalsIgnoreCase(mode)) {
            return "当前模式 " + mode + " 不支持 PULL 同步";
        }
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SYNC_IN_PROGRESS);
        }
        OrgSyncState.setRunning(true);
        try {
            // 未配置厂商 Adapter：留空操作（PULL 需按厂商实现 OrgSyncAdapter）
            String result = "PULL 同步完成（未配置厂商 Adapter，空操作）";
            OrgSyncState.record(Instant.now(), result);
            return result;
        } finally {
            OrgSyncState.setRunning(false);
            lock.unlock();
        }
    }

    public SyncStatusVO status() {
        return new SyncStatusVO(OrgSyncState.running(), OrgSyncState.lastSyncAt(), OrgSyncState.lastResult(), mode);
    }

    /* ================= PUSH 模式 ================= */

    @Transactional
    public void applyPush(OrgPushPayload payload) {
        requireMode("PUSH");
        Map<String, Long> deptIdByExternal = new HashMap<>();

        List<OrgPushPayload.OrgPushDept> depts = payload.departments() == null ? List.of() : payload.departments();
        for (OrgPushPayload.OrgPushDept d : depts) {
            Long id = upsertDept(d, deptIdByExternal);
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
                    linkUserDept(uid, did, de.equals(u.primaryDeptExternalId()));
                }
            }
        }

        if (payload.deleted() != null) {
            if (payload.deleted().userExternalIds() != null) {
                for (String ext : payload.deleted().userExternalIds()) {
                    SysUser u = findByExternalId(ext);
                    if (u != null) {
                        u.setStatus("DISABLED");
                        userMapper.updateById(u);
                    }
                }
            }
            if (payload.deleted().deptExternalIds() != null) {
                for (String ext : payload.deleted().deptExternalIds()) {
                    Department d = findByCorpId(ext);
                    if (d != null) {
                        departmentMapper.deleteById(d.getId());
                    }
                }
            }
        }
        OrgSyncState.record(Instant.now(), "PUSH 接收成功：depts=" + depts.size() + " users=" + users.size());
    }

    private Long upsertDept(OrgPushPayload.OrgPushDept d, Map<String, Long> map) {
        Department exist = findByCorpId(d.externalId());
        if (exist == null) {
            Department row = new Department();
            row.setName(d.name());
            row.setCorpId(d.externalId());
            row.setSort(d.sort());
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
        SysUser exist = findByExternalId(u.externalId());
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

    /* ================= SCIM 模式 ================= */

    @Transactional
    public void scimUpsertUser(Map<String, Object> resource) {
        requireMode("SCIM");
        String externalId = firstNonBlank(str(resource.get("externalId")), str(resource.get("id")),
                str(resource.get("userName")));
        if (externalId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SCIM 用户缺少标识");
        }
        String name = null;
        Object n = resource.get("name");
        if (n instanceof Map<?, ?> nm) {
            String given = str(nm.get("givenName"));
            String family = str(nm.get("familyName"));
            name = (family == null ? "" : family) + (given == null ? "" : given);
            if (name.isBlank()) {
                name = null;
            }
        }
        String userName = str(resource.get("userName"));
        Boolean active = resource.get("active") instanceof Boolean b ? b : null;
        SysUser exist = findByExternalId(externalId);
        if (exist == null) {
            SysUser row = new SysUser();
            row.setExternalId(externalId);
            row.setUsername(userName != null ? userName : externalId);
            row.setName(name != null ? name : (userName != null ? userName : externalId));
            row.setEmail(str(resource.get("emails-first")));
            row.setRoleCode("COMMON");
            row.setStatus(Boolean.FALSE.equals(active) ? "DISABLED" : "ACTIVE");
            row.setMustChangePassword(0);
            userMapper.insert(row);
        } else {
            if (name != null) {
                exist.setName(name);
            }
            if (active != null) {
                exist.setStatus(active ? "ACTIVE" : "DISABLED");
            }
            userMapper.updateById(exist);
        }
        OrgSyncState.record(Instant.now(), "SCIM 用户 upsert: " + externalId);
    }

    @Transactional
    public void scimUpsertGroup(Map<String, Object> resource) {
        requireMode("SCIM");
        String externalId = firstNonBlank(str(resource.get("externalId")), str(resource.get("id")));
        String displayName = firstNonBlank(str(resource.get("displayName")), externalId);
        if (externalId == null || displayName == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SCIM 组缺少标识/名称");
        }
        Department exist = findByCorpId(externalId);
        Long deptId;
        if (exist == null) {
            Department row = new Department();
            row.setName(displayName);
            row.setCorpId(externalId);
            row.setSort(0);
            row.setCreatedAt(Instant.now());
            departmentMapper.insert(row);
            deptId = row.getId();
        } else {
            exist.setName(displayName);
            exist.setUpdatedAt(Instant.now());
            departmentMapper.updateById(exist);
            deptId = exist.getId();
        }
        // members[].value → sys_user_department
        if (resource.get("members") instanceof List<?> members) {
            for (Object m : members) {
                if (m instanceof Map<?, ?> mm) {
                    String memberId = str(mm.get("value"));
                    if (memberId != null) {
                        SysUser u = findByExternalId(memberId);
                        if (u == null) {
                            u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                                    .eq(SysUser::getUsername, memberId).last("limit 1"));
                        }
                        if (u != null) {
                            linkUserDept(u.getId(), deptId, false);
                        }
                    }
                }
            }
        }
        OrgSyncState.record(Instant.now(), "SCIM 组 upsert: " + externalId);
    }

    /* ================= helpers ================= */

    private void requireMode(String expected) {
        if (!expected.equalsIgnoreCase(mode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "组织同步模式未启用：" + expected);
        }
    }

    private Department findByCorpId(String corpId) {
        return departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                .eq(Department::getCorpId, corpId).last("limit 1"));
    }

    private SysUser findByExternalId(String externalId) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getExternalId, externalId).last("limit 1"));
    }

    private void linkUserDept(Long userId, Long deptId, boolean primary) {
        Long cnt = userDepartmentMapper.selectCount(Wrappers.<UserDepartment>lambdaQuery()
                .eq(UserDepartment::getUserId, userId).eq(UserDepartment::getDeptId, deptId));
        if (cnt != null && cnt > 0) {
            return;
        }
        UserDepartment ud = new UserDepartment();
        ud.setUserId(userId);
        ud.setDeptId(deptId);
        ud.setIsPrimary(primary ? 1 : 0);
        userDepartmentMapper.insert(ud);
    }

    private String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
