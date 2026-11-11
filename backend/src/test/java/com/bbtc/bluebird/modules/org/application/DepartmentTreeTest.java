package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.modules.org.domain.Department;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 部门树派生逻辑（全路径 / 先序）。导出与导入共用这套口径，所以这里锁的是「闭环」前提：
 * 导出写出的父路径，必须能被导入按同一套 paths() 反解回去。
 */
class DepartmentTreeTest {

    @Test
    void 顶级部门路径就是自身名称() {
        Department root = dept(1L, "总部", null, 0);
        Map<Long, String> paths = DepartmentTree.paths(List.of(root));

        assertEquals("总部", paths.get(1L));
        assertEquals("", DepartmentTree.parentPathOf(root, paths), "顶级部门没有父路径");
    }

    @Test
    void 多级部门拼接全路径() {
        Department root = dept(1L, "XX公司", null, 0);
        Department mid = dept(2L, "研发中心", 1L, 0);
        Department leaf = dept(3L, "平台组", 2L, 0);

        Map<Long, String> paths = DepartmentTree.paths(List.of(root, mid, leaf));

        assertEquals("XX公司/研发中心", paths.get(2L));
        assertEquals("XX公司/研发中心/平台组", paths.get(3L));
        assertEquals("XX公司/研发中心", DepartmentTree.parentPathOf(leaf, paths));
    }

    @Test
    void 父节点缺失时截断而不是抛错() {
        // 历史数据可能出现父部门已被删除的孤儿节点
        Department orphan = dept(9L, "孤儿部门", 404L, 0);

        Map<Long, String> paths = DepartmentTree.paths(List.of(orphan));

        assertEquals("孤儿部门", paths.get(9L));
        assertEquals("", DepartmentTree.parentPathOf(orphan, paths), "父级查不到时按顶级处理");
    }

    @Test
    void 成环时不死循环() {
        Department a = dept(1L, "A", 2L, 0);
        Department b = dept(2L, "B", 1L, 0);

        Map<Long, String> paths = DepartmentTree.paths(List.of(a, b));

        // 只要能在有限步内结束、且路径里不含重复自己即可
        assertEquals(2, paths.size());
        assertTrue(paths.get(1L).contains("A"));
        assertTrue(paths.get(1L).length() < 20, "环保护必须生效，不能无限拼接");
    }

    @Test
    void 先序排列让父部门排在子部门之前() {
        Department rootB = dept(2L, "B公司", null, 1);
        Department rootA = dept(1L, "A公司", null, 0);
        Department childA2 = dept(4L, "A-子2", 1L, 5);
        Department childA1 = dept(3L, "A-子1", 1L, 2);

        List<Department> ordered = DepartmentTree.ordered(List.of(childA2, rootB, childA1, rootA));

        assertEquals(List.of(1L, 3L, 4L, 2L), ordered.stream().map(Department::getId).toList(),
                "根按 sort 排序，子节点紧跟父节点且自身按 sort 排序");
    }

    @Test
    void 相同排序时按id稳定排序() {
        Department b = dept(5L, "同名排序2", null, 0);
        Department a = dept(3L, "同名排序1", null, 0);

        List<Department> ordered = DepartmentTree.ordered(List.of(b, a));

        assertEquals(List.of(3L, 5L), ordered.stream().map(Department::getId).toList());
    }

    @Test
    void sort为空时按0处理() {
        Department late = dept(7L, "有排序", null, 3);
        Department none = dept(8L, "无排序", null, null);

        List<Department> ordered = DepartmentTree.ordered(List.of(late, none));

        assertEquals(List.of(8L, 7L), ordered.stream().map(Department::getId).toList());
    }

    @Test
    void 导出顺序与路径口径一致_父路径可在paths中反查() {
        Department root = dept(1L, "总部", null, 0);
        Department child = dept(2L, "研发部", 1L, 0);

        List<Department> ordered = DepartmentTree.ordered(List.of(child, root));
        Map<Long, String> paths = DepartmentTree.paths(ordered);

        // 导入时就是用「父路径 → id」这张表反解父级
        Map<String, Long> idByPath = new java.util.HashMap<>();
        paths.forEach((id, p) -> idByPath.put(p, id));
        assertEquals(1L, idByPath.get(DepartmentTree.parentPathOf(child, paths)));
    }

    private Department dept(Long id, String name, Long parentId, Integer sort) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        d.setParentId(parentId);
        d.setSort(sort);
        return d;
    }
}
