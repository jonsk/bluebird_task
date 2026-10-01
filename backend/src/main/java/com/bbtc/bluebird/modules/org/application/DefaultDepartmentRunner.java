package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 系统默认顶级部门初始化（用户反馈 #5）。
 *
 * <p>「组织管理」需要一个初始化即可用的顶级部门：<b>可改名、不可删除</b>
 * （删除由 {@link DepartmentService#delete} 依 {@code is_system} 拒绝）。
 * 新建用户时部门为必填，因此必须保证系统里至少存在一个可选部门。
 *
 * <p>三种情形：
 * <ol>
 *   <li>已存在 {@code is_system=1} 的部门 → 无需处理（幂等，避免重启重复插入）；</li>
 *   <li>已有顶级部门但都没标记（旧库升级）→ 把最早创建的顶级部门提升为系统部门，维持不变量；</li>
 *   <li>完全没有部门 → 创建默认顶级部门「总公司」。</li>
 * </ol>
 *
 * <p>随后把无部门用户归入该默认部门（幂等）。执行顺序排在 {@code SeedAdminRunner} 之后
 * （见 {@code @Order}），因此首次启动的种子 ADMIN 也能在同一轮被归入默认部门。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DefaultDepartmentRunner implements ApplicationRunner {

    /** 默认顶级部门名称（部署后可自行改名）。 */
    static final String DEFAULT_NAME = "XX公司";

    /**
     * 历史默认名：旧版本种子用过的名字，启动时若**未被改名**则迁到 {@link #DEFAULT_NAME}。
     * 顺序即迁移链：总公司 → 部门 → XX公司。
     */
    private static final List<String> LEGACY_DEFAULT_NAMES = List.of("总公司", "部门");

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long systemDeptId = ensureDefaultDepartment();
        backfillOrphanUsers(systemDeptId);
    }

    /** 保证存在且仅存在一个受保护的顶级部门，返回其 id。 */
    private Long ensureDefaultDepartment() {
        List<Department> marked = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .eq(Department::getIsSystem, 1).orderByAsc(Department::getId));
        if (!marked.isEmpty()) {
            return renameLegacyDefault(marked.get(0));
        }
        List<Department> roots = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .isNull(Department::getParentId)
                .orderByAsc(Department::getId));
        if (!roots.isEmpty()) {
            Department d = roots.get(0);
            d.setIsSystem(1);
            d.setUpdatedAt(Instant.now());
            departmentMapper.updateById(d);
            log.info("已将顶级部门「{}」标记为系统默认部门（可改名，不可删除）", d.getName());
            return d.getId();
        }
        Department d = new Department();
        d.setName(DEFAULT_NAME);
        d.setParentId(null);
        d.setSort(0);
        d.setIsSystem(1);
        d.setCreatedAt(Instant.now());
        departmentMapper.insert(d);
        log.info("已创建系统默认顶级部门「{}」（可改名，不可删除）", DEFAULT_NAME);
        return d.getId();
    }

    /**
     * 历史默认名迁移：系统默认部门若仍是旧种子名（「总公司」/「部门」）则改为 {@link #DEFAULT_NAME}。
     *
     * <p>只在名称**未被改名**时迁移，避免覆盖部署方自定义的名称。
     */
    private Long renameLegacyDefault(Department d) {
        if (LEGACY_DEFAULT_NAMES.contains(d.getName())) {
            String old = d.getName();
            d.setName(DEFAULT_NAME);
            d.setUpdatedAt(Instant.now());
            departmentMapper.updateById(d);
            log.info("系统默认部门名称已由「{}」更新为「{}」", old, DEFAULT_NAME);
        }
        return d.getId();
    }

    /**
     * 把「无部门」用户归入系统默认部门。
     *
     * <p>新建用户已强制选择部门（用户反馈 #5），但 OIDC/SCIM 自动建号与 ADR-007 之前的旧账号
     * 可能没有部门；此处兜底，保证「每个用户都有部门」这一不变量成立（幂等，仅首次启动会改动）。
     */
    private void backfillOrphanUsers(Long systemDeptId) {
        List<SysUser> orphans = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId)
                .isNull(SysUser::getDeptId));
        if (orphans.isEmpty()) {
            return;
        }
        for (SysUser u : orphans) {
            SysUser patch = new SysUser();
            patch.setId(u.getId());
            patch.setDeptId(systemDeptId);
            userMapper.updateById(patch);
        }
        log.info("已将 {} 个无部门用户归入系统默认部门（含自动建号/历史账号）", orphans.size());
    }
}
