package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.task.domain.Category;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.dto.CategoryCmd;
import com.bbtc.bluebird.modules.task.dto.CategoryNodeDTO;
import com.bbtc.bluebird.modules.task.infrastructure.CategoryMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类服务（ADR-015，02 §4.7）。读：PERSONAL(我)/DEPARTMENT(我部门)/ORG(全部)；写：scope 授权。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final TaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public List<CategoryNodeDTO> tree(String scopeFilter) {
        Long me = UserContext.currentUserId();
        Long myDept = currentDeptId(me);
        boolean manager = isAdminOrUserManager();
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
        List<Category> visible = all.stream().filter(c -> isVisible(c, me, myDept, manager)).toList();
        if (StringUtils.hasText(scopeFilter)) {
            visible = visible.stream().filter(c -> scopeFilter.equals(c.getScope())).toList();
        }
        Map<Long, Integer> counts = taskCountsByCategory();
        Map<Long, CategoryNodeDTO> map = new HashMap<>();
        for (Category c : visible) {
            CategoryNodeDTO node = new CategoryNodeDTO(c.getId(), c.getName(), c.getParentId(), c.getScope(),
                    c.getDeptId(), c.getOwnerId(), c.getSort(), c.getCreatedAt(), c.getUpdatedAt());
            node.taskCount = counts.getOrDefault(c.getId(), 0);
            map.put(c.getId(), node);
        }
        List<CategoryNodeDTO> roots = new ArrayList<>();
        for (Category c : visible) {
            CategoryNodeDTO node = map.get(c.getId());
            CategoryNodeDTO parent = c.getParentId() == null ? null : map.get(c.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
                parent.taskCount += node.taskCount;
            }
        }
        return roots;
    }

    @Transactional
    public Long create(CategoryCmd cmd) {
        Long me = UserContext.currentUserId();
        String scope = StringUtils.hasText(cmd.scope()) ? cmd.scope() : Category.PERSONAL;
        Long deptId = normalizeDept(scope, cmd.deptId(), me);
        assertWritable(null, scope, deptId, me);
        Category c = new Category();
        c.setName(cmd.name());
        c.setParentId(cmd.parentId());
        c.setScope(scope);
        c.setDeptId(deptId);
        c.setOwnerId(me);
        c.setSort(0);
        c.setVersion(0);
        c.setCreatedAt(Instant.now());
        categoryMapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void update(Long id, CategoryCmd cmd) {
        Long me = UserContext.currentUserId();
        Category c = require(id);
        String scope = StringUtils.hasText(cmd.scope()) ? cmd.scope() : c.getScope();
        // 仅改名/移动时不重算部门：保留既有 dept_id（否则 ADMIN 改他人部门分类会被改挂或报错）
        Long deptId = normalizeDeptForUpdate(scope, cmd.deptId(), c.getDeptId(), me);
        assertWritable(c.getOwnerId(), scope, deptId, me);
        if (StringUtils.hasText(cmd.name())) {
            c.setName(cmd.name());
        }
        c.setParentId(cmd.parentId());
        c.setScope(scope);
        c.setDeptId(deptId);
        categoryMapper.updateById(c);
    }

    @Transactional
    public void delete(Long id) {
        Long me = UserContext.currentUserId();
        Category c = require(id);
        assertWritable(c.getOwnerId(), c.getScope(), c.getDeptId(), me);
        Long children = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子分类，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 写权限（ADR-015 §3）：
     * <ul>
     *   <li>{@code PERSONAL}：仅创建者本人；</li>
     *   <li>{@code DEPARTMENT}：创建者 / 该部门负责人（{@code leader_user_id}） / {@code ADMIN}；</li>
     *   <li>{@code ORG}：{@code ADMIN} / {@code USER_MANAGER}。</li>
     * </ul>
     *
     * @param ownerId 既有分类的创建者；创建时为 {@code null}
     */
    private void assertWritable(Long ownerId, String scope, Long deptId, Long me) {
        String role = UserContext.currentRoleCode();
        if ("ADMIN".equals(role)) {
            return;
        }
        switch (scope) {
            case Category.ORG -> {
                if (!"USER_MANAGER".equals(role)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "组织分类仅 ADMIN/USER_MANAGER 可写");
                }
            }
            case Category.DEPARTMENT -> {
                boolean creator = ownerId != null && ownerId.equals(me);
                if (!creator && !isDeptLeader(deptId, me)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建者或本部门负责人可写部门分类");
                }
            }
            default -> {
                // PERSONAL：仅创建者本人（防止越权改/删他人个人分类）
                if (ownerId != null && !ownerId.equals(me)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建者可写个人分类");
                }
            }
        }
    }

    private boolean isDeptLeader(Long deptId, Long me) {
        if (deptId == null || me == null) {
            return false;
        }
        Department d = departmentMapper.selectById(deptId);
        return d != null && me.equals(d.getLeaderUserId());
    }

    private Long normalizeDept(String scope, Long deptId, Long me) {
        if (Category.DEPARTMENT.equals(scope)) {
            Long d = deptId != null ? deptId : currentDeptId(me);
            if (d == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "部门分类须指定 deptId");
            }
            return d;
        }
        return null;
    }

    /** 更新时：未显式指定 deptId 则沿用既有部门，避免误改挂载。 */
    private Long normalizeDeptForUpdate(String scope, Long cmdDeptId, Long existingDeptId, Long me) {
        if (!Category.DEPARTMENT.equals(scope)) {
            return null;
        }
        if (cmdDeptId != null) {
            return cmdDeptId;
        }
        if (existingDeptId != null) {
            return existingDeptId;
        }
        return normalizeDept(scope, null, me);
    }

    private boolean isVisible(Category c, Long me, Long myDept, boolean manager) {
        return switch (c.getScope()) {
            case Category.ORG -> true;
            // 管理员/用户管理员可管理各部门分类；否则仅本部门可见。
            // 修复「部门分类无法建立/建立后看不到」：ADMIN 往往自身没有部门（dept_id 为空），
            // 若只按 myDept 判定，则建好后连创建者本人都不可见。
            case Category.DEPARTMENT -> manager || (myDept != null && myDept.equals(c.getDeptId()));
            default -> me.equals(c.getOwnerId());
        };
    }

    private boolean isAdminOrUserManager() {
        String role = UserContext.currentRoleCode();
        return "ADMIN".equals(role) || "USER_MANAGER".equals(role);
    }

    private Long currentDeptId(Long me) {
        SysUser u = userMapper.selectById(me);
        return u == null ? null : u.getDeptId();
    }

    private Category require(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return c;
    }

    private Map<Long, Integer> taskCountsByCategory() {
        Map<Long, Integer> counts = new HashMap<>();
        // 只统计有分类的任务。必须加 isNotNull：MyBatis 对「被选中的列全为 NULL」的行会返回 null 元素，
        // 而这里唯一的选中列 category_id 可空 → 任何「无分类任务」都会产生 null 元素，
        // 原实现在 t.getCategoryId() 处 NPE，导致 GET /categories 整体 10000、左侧分类树渲染失败。
        for (Task t : taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getCategoryId)
                .isNotNull(Task::getCategoryId))) {
            if (t != null && t.getCategoryId() != null) {
                counts.merge(t.getCategoryId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** 分类 id 及其后代（用于校验/计数扩展）。 */
    public Set<Long> descendants(Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            if (result.add(cur)) {
                categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                                .select(Category::getId).eq(Category::getParentId, cur))
                        .forEach(c -> queue.add(c.getId()));
            }
        }
        return result;
    }
}
