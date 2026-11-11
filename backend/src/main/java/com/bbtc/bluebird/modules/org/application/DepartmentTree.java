package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.modules.org.domain.Department;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门树的只读派生：全路径、先序排列。
 *
 * <p>从 {@link DepartmentExcelService} 抽出：导出要按树序写行、导入要把「全路径」解析回父级 id，
 * 两边必须用同一套路径口径（含环保护），否则「导出→改→导入」不闭环。
 */
final class DepartmentTree {

    private static final String PATH_SEP = "/";
    private static final int DEFAULT_SORT = 0;

    private DepartmentTree() {
    }

    /** id → 全路径（顶级即名称本身）；遇到环或断链时截断，避免死循环。 */
    static Map<Long, String> paths(List<Department> all) {
        Map<Long, Department> byId = new HashMap<>();
        all.forEach(d -> byId.put(d.getId(), d));
        Map<Long, String> out = new HashMap<>();
        for (Department d : all) {
            StringBuilder sb = new StringBuilder(d.getName());
            Department cur = d;
            Set<Long> guard = new HashSet<>();
            guard.add(d.getId());
            while (cur.getParentId() != null) {
                Department parent = byId.get(cur.getParentId());
                if (parent == null || !guard.add(parent.getId())) {
                    break;
                }
                sb.insert(0, parent.getName() + PATH_SEP);
                cur = parent;
            }
            out.put(d.getId(), sb.toString());
        }
        return out;
    }

    /** 父部门的全路径；顶级部门返回空串。 */
    static String parentPathOf(Department d, Map<Long, String> paths) {
        if (d.getParentId() == null) {
            return "";
        }
        String p = paths.get(d.getParentId());
        return p == null ? "" : p;
    }

    /** 树的先序（按 sort、id），使导出的父子顺序可直接回填导入。 */
    static List<Department> ordered(List<Department> all) {
        Map<Long, List<Department>> children = new LinkedHashMap<>();
        List<Department> roots = new ArrayList<>();
        for (Department d : all) {
            if (d.getParentId() == null) {
                roots.add(d);
            } else {
                children.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
            }
        }
        Comparator<Department> cmp = Comparator
                .comparingInt((Department d) -> d.getSort() == null ? DEFAULT_SORT : d.getSort())
                .thenComparing(Department::getId);
        roots.sort(cmp);
        List<Department> out = new ArrayList<>();
        for (Department root : roots) {
            walk(root, children, cmp, out);
        }
        return out;
    }

    private static void walk(Department node, Map<Long, List<Department>> children,
                             Comparator<Department> cmp, List<Department> out) {
        out.add(node);
        List<Department> kids = children.get(node.getId());
        if (kids == null || kids.isEmpty()) {
            return;
        }
        kids.sort(cmp);
        for (Department kid : kids) {
            walk(kid, children, cmp, out);
        }
    }
}
