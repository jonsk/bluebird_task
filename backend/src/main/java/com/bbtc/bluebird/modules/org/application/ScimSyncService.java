package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 组织同步 SCIM 模式（02 §3.3 模式 1）：把 SCIM User/Group 资源映射为本地用户/部门。
 *
 * <p>语义约束（与旧实现保持一致，勿改）：
 * <ul>
 *   <li>Group 一律映射为部门，不映射角色权限；</li>
 *   <li>删除不物理删用户，只按 {@code active=false} 置 DISABLED；</li>
 *   <li>externalId 缺失时依次回落到 {@code id} / {@code userName}（用户）与 {@code id}（组）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScimSyncService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final OrgDirectorySupport support;

    @Transactional
    public void upsertUser(Map<String, Object> resource) {
        support.requireMode("SCIM");
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
        SysUser exist = support.findUserByExternalId(externalId);
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
    public void upsertGroup(Map<String, Object> resource) {
        support.requireMode("SCIM");
        String externalId = firstNonBlank(str(resource.get("externalId")), str(resource.get("id")));
        String displayName = firstNonBlank(str(resource.get("displayName")), externalId);
        if (externalId == null || displayName == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SCIM 组缺少标识/名称");
        }
        Department exist = support.findDeptByCorpId(externalId);
        Long deptId;
        if (exist == null) {
            Department row = new Department();
            row.setName(displayName);
            row.setCorpId(externalId);
            row.setSort(0);
            row.setIsSystem(0);
            row.setCreatedAt(Instant.now());
            departmentMapper.insert(row);
            deptId = row.getId();
        } else {
            exist.setName(displayName);
            exist.setUpdatedAt(Instant.now());
            departmentMapper.updateById(exist);
            deptId = exist.getId();
        }
        // members[].value → sys_user_department（先按 externalId 找，再按用户名兜底）
        if (resource.get("members") instanceof List<?> members) {
            for (Object m : members) {
                if (m instanceof Map<?, ?> mm) {
                    String memberId = str(mm.get("value"));
                    if (memberId != null) {
                        SysUser u = support.findUserByExternalId(memberId);
                        if (u == null) {
                            u = support.findUserByUsername(memberId);
                        }
                        if (u != null) {
                            support.linkUserDept(u.getId(), deptId, false);
                        }
                    }
                }
            }
        }
        OrgSyncState.record(Instant.now(), "SCIM 组 upsert: " + externalId);
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
