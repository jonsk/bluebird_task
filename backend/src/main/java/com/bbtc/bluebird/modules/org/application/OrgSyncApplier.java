package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.dto.OrgDept;
import com.bbtc.bluebird.modules.org.dto.OrgPullData;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PULL 同步的落库执行器（独立 Bean，保证 {@code @Transactional} 生效）。
 *
 * <p>与 {@link OrgSyncService} 拆分的原因：同类内部调用不会经过 Spring 代理，事务注解会失效。
 */
@Service
@RequiredArgsConstructor
public class OrgSyncApplier {

    private static final String SOURCE_CASDOOR = "CASDOOR";

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    /** 落库统计。 */
    public record Result(int deptTotal, int deptInserted, int deptUpdated,
                         int userTotal, int userInserted, int userUpdated, int userSkipped) {
    }

    @Transactional
    public Result apply(OrgPullData data) {
        int deptTotal = 0;
        int deptInserted = 0;
        int deptUpdated = 0;
        int userTotal = 0;
        int userInserted = 0;
        int userUpdated = 0;
        int userSkipped = 0;

        Map<String, Long> deptIdByExternal = new HashMap<>();
        List<OrgDept> depts = data.departments() == null ? List.of() : data.departments();

        // 1) 部门 upsert（父级稍后回填，兼容任意输入顺序）
        for (OrgDept d : depts) {
            if (!StringUtils.hasText(d.externalId())) {
                continue;
            }
            Department exist = findByCorpId(d.externalId());
            Long id = upsertDept(d, exist);
            deptIdByExternal.put(d.externalId(), id);
            deptTotal++;
            if (exist == null) {
                deptInserted++;
            } else {
                deptUpdated++;
            }
        }
        for (OrgDept d : depts) {
            if (!StringUtils.hasText(d.parentExternalId())) {
                continue;
            }
            Long id = deptIdByExternal.get(d.externalId());
            Long parentId = deptIdByExternal.get(d.parentExternalId());
            if (id == null || parentId == null || id.equals(parentId)) {
                continue;
            }
            Department row = departmentMapper.selectById(id);
            if (row != null && !parentId.equals(row.getParentId())) {
                row.setParentId(parentId);
                row.setUpdatedAt(Instant.now());
                departmentMapper.updateById(row);
            }
        }

        // 2) 用户 upsert
        List<OrgDept.OrgUser> users = data.users() == null ? List.of() : data.users();
        Set<String> seenUserExternal = new HashSet<>();
        for (OrgDept.OrgUser u : users) {
            if (!StringUtils.hasText(u.externalId())) {
                userSkipped++;
                continue;
            }
            SysUser exist = findByExternalId(u.externalId());
            upsertUser(u, exist, deptIdByExternal);
            seenUserExternal.add(u.externalId());
            userTotal++;
            if (exist == null) {
                userInserted++;
            } else {
                userUpdated++;
            }
        }

        // 3) 用户-部门关系：全量重映射（先清后建）
        for (OrgDept.OrgUser u : users) {
            if (!StringUtils.hasText(u.externalId())) {
                continue;
            }
            SysUser row = findByExternalId(u.externalId());
            if (row == null) {
                continue;
            }
            userDepartmentMapper.delete(Wrappers.<UserDepartment>lambdaQuery()
                    .eq(UserDepartment::getUserId, row.getId()));
            List<String> deptExts = u.deptExternalIds() == null ? List.of() : u.deptExternalIds();
            for (String ext : deptExts) {
                Long deptId = deptIdByExternal.get(ext);
                if (deptId != null) {
                    linkUserDept(row.getId(), deptId, ext.equals(u.primaryDeptExternalId()));
                }
            }
        }

        // 4) 外部源已移除的：用户置 DISABLED、部门软删（拉取为空时跳过，防误清）
        if (!users.isEmpty()) {
            disableMissingUsers(seenUserExternal);
        }
        if (!depts.isEmpty()) {
            deleteMissingDepartments(deptIdByExternal.keySet());
        }

        return new Result(deptTotal, deptInserted, deptUpdated,
                userTotal, userInserted, userUpdated, userSkipped);
    }

    // ==================== helpers ====================

    private Long upsertDept(OrgDept d, Department exist) {
        if (exist == null) {
            Department row = new Department();
            row.setName(StringUtils.hasText(d.name()) ? d.name() : d.externalId());
            row.setCorpId(d.externalId());
            row.setExternalSource(SOURCE_CASDOOR);
            row.setSort(d.sort() == null ? 0 : d.sort());
            row.setIsSystem(0);
            row.setCreatedAt(Instant.now());
            row.setUpdatedAt(Instant.now());
            departmentMapper.insert(row);
            return row.getId();
        }
        exist.setName(StringUtils.hasText(d.name()) ? d.name() : exist.getName());
        if (d.sort() != null) {
            exist.setSort(d.sort());
        }
        exist.setExternalSource(SOURCE_CASDOOR);
        exist.setUpdatedAt(Instant.now());
        departmentMapper.updateById(exist);
        return exist.getId();
    }

    private void upsertUser(OrgDept.OrgUser u, SysUser exist, Map<String, Long> deptMap) {
        Long primaryDeptId = StringUtils.hasText(u.primaryDeptExternalId())
                ? deptMap.get(u.primaryDeptExternalId()) : null;
        if (exist == null) {
            SysUser row = new SysUser();
            row.setExternalId(u.externalId());
            row.setExternalSource(SOURCE_CASDOOR);
            row.setUsername(StringUtils.hasText(u.username()) ? u.username() : u.externalId());
            row.setName(StringUtils.hasText(u.name()) ? u.name() : row.getUsername());
            row.setMobile(u.mobile());
            row.setEmail(u.email());
            row.setAvatarUrl(u.avatar());
            row.setDeptId(primaryDeptId);
            row.setRoleCode(StringUtils.hasText(u.roleCode()) ? u.roleCode() : "COMMON");
            row.setStatus(StringUtils.hasText(u.status()) ? u.status() : "ACTIVE");
            row.setMustChangePassword(0);
            userMapper.insert(row);
            return;
        }
        if (StringUtils.hasText(u.name())) {
            exist.setName(u.name());
        }
        exist.setMobile(u.mobile());
        exist.setEmail(u.email());
        if (StringUtils.hasText(u.avatar())) {
            exist.setAvatarUrl(u.avatar());
        }
        if (primaryDeptId != null) {
            exist.setDeptId(primaryDeptId);
        }
        if (StringUtils.hasText(u.roleCode())) {
            exist.setRoleCode(u.roleCode());
        }
        exist.setExternalSource(SOURCE_CASDOOR);
        exist.setStatus(StringUtils.hasText(u.status()) ? u.status() : "ACTIVE");
        userMapper.updateById(exist);
    }

    private void disableMissingUsers(Set<String> seen) {
        List<SysUser> existing = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getExternalSource, SOURCE_CASDOOR));
        for (SysUser u : existing) {
            if (u.getExternalId() != null && !seen.contains(u.getExternalId())
                    && !"DISABLED".equals(u.getStatus())) {
                u.setStatus("DISABLED");
                userMapper.updateById(u);
            }
        }
    }

    private void deleteMissingDepartments(Set<String> seen) {
        List<Department> existing = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .eq(Department::getExternalSource, SOURCE_CASDOOR));
        for (Department d : existing) {
            if (d.getCorpId() != null && !seen.contains(d.getCorpId())) {
                departmentMapper.deleteById(d.getId());
            }
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
}
