package com.bbtc.bluebird.modules.org.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 部门可见性策略（INTERNAL 方案 2 的**单一事实来源**）。
 *
 * <p>{@code visibleDeptIds(me) = deptAndDescendants(deptIdOf(me))}：本级 + 全部子级（父看子、子不看父）。
 *
 * <p><b>SQL 与 Java 同源</b>：
 * <ul>
 *   <li>SQL 侧（列表/日历/标签 facet）用 {@link #visibleDeptSql(Long)} 的递归 CTE 子查询，
 *       一条 SQL 内完成部门树展开 + 排序 + 分页 + COUNT，避免 SQLite 999 绑定参数上限；</li>
 *   <li>Java 侧（{@code NoteAuthService.isVisible}）用 {@link #visibleDeptIds(Long)} 的集合。</li>
 * </ul>
 * 两者必须表达同一集合，禁止各写一套。
 *
 * <p>部门规模上限与降级：组织数千部门时禁止「多次查询后内存并集」；递归 CTE 由 SQLite 单条执行。
 * 若未来部门树需要 Java 侧逻辑无法下推，降级为单条 {@code OR} 分组或临时表 JOIN。
 */
@Component
@RequiredArgsConstructor
public class DepartmentPolicy {

    private final CurrentUserDeptService currentUserDeptService;
    private final DepartmentService departmentService;

    /** 当前用户可见的部门集合（含本级与全部后代）；无部门返回空集。 */
    public Set<Long> visibleDeptIds(Long userId) {
        Long deptId = currentUserDeptService.deptIdOf(userId);
        if (deptId == null) {
            return Set.of();
        }
        return departmentService.deptAndDescendants(deptId);
    }

    /**
     * 当前用户可见部门的 SQL 子查询（可用于 {@code dept_id IN (...)}）；无部门返回 {@code null}。
     *
     * <p>形如 {@code (WITH RECURSIVE note_dept_tree(id) AS (...) SELECT id FROM note_dept_tree)}。
     */
    public String visibleDeptSql(Long userId) {
        Long deptId = currentUserDeptService.deptIdOf(userId);
        if (deptId == null) {
            return null;
        }
        return "(WITH RECURSIVE note_dept_tree(id) AS ("
                + " SELECT id FROM sys_department WHERE id = " + deptId + " AND deleted = 0"
                + " UNION"
                + " SELECT d.id FROM sys_department d JOIN note_dept_tree t ON d.parent_id = t.id WHERE d.deleted = 0"
                + ") SELECT id FROM note_dept_tree)";
    }

    /**
     * 当前用户下属 owner 的 SQL 子查询（{@code owner_id IN (...)}）。
     *
     * <p>与 {@link DepartmentService#subordinates(Long)} 等价：我负责的部门及其后代内的所有用户（排除本人）。
     */
    public String subordinateOwnerSql(Long userId) {
        return "(SELECT u.id FROM sys_user u WHERE u.deleted = 0 AND u.dept_id IN ("
                + "WITH RECURSIVE note_sub_dept(id) AS ("
                + " SELECT d.id FROM sys_department d WHERE d.leader_user_id = " + userId + " AND d.deleted = 0"
                + " UNION"
                + " SELECT d2.id FROM sys_department d2 JOIN note_sub_dept s ON d2.parent_id = s.id WHERE d2.deleted = 0"
                + ") SELECT id FROM note_sub_dept))";
    }
}
