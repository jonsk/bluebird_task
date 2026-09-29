package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
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

    public List<CategoryNodeDTO> tree(String scopeFilter) {
        Long me = UserContext.currentUserId();
        Long myDept = currentDeptId(me);
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
        List<Category> visible = all.stream().filter(c -> isVisible(c, me, myDept)).toList();
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
        assertWritableScope(scope, deptId, me, true);
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
        Long deptId = normalizeDept(scope, cmd.deptId(), me);
        assertWritableScope(scope, deptId, me, false);
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
        assertWritableScope(c.getScope(), c.getDeptId(), me, false);
        Long children = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子分类，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private void assertWritableScope(String scope, Long deptId, Long me, boolean creating) {
        if (UserContext.isAdmin()) {
            return;
        }
        switch (scope) {
            case Category.ORG -> throw new BusinessException(ErrorCode.FORBIDDEN, "组织分类仅 ADMIN/USER_MANAGER 可写");
            case Category.DEPARTMENT -> {
                if (deptId == null || !deptId.equals(currentDeptId(me))) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "仅本部门可写部门分类");
                }
            }
            default -> {
                // PERSONAL：创建者本人
            }
        }
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

    private boolean isVisible(Category c, Long me, Long myDept) {
        return switch (c.getScope()) {
            case Category.ORG -> true;
            case Category.DEPARTMENT -> myDept != null && myDept.equals(c.getDeptId());
            default -> me.equals(c.getOwnerId());
        };
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
        for (Task t : taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getCategoryId))) {
            if (t.getCategoryId() != null) {
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
