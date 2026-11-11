package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.application.OrgSyncApplier;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.dto.OrgDept;
import com.bbtc.bluebird.modules.org.dto.OrgPullData;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PULL 落库执行器（{@link OrgSyncApplier}）：部门两遍处理、用户 upsert、关系全量重映射、
 * 以及「外部源已删除 → 用户禁用 / 部门删除」这三个最容易出事故的分支。
 *
 * <p>该类此前 0% 覆盖，而它是 PULL 同步唯一真正写库的地方。
 */
class OrgSyncApplierTest {

    private DepartmentMapper departmentMapper;
    private SysUserMapper userMapper;
    private UserDepartmentMapper userDepartmentMapper;
    private OrgSyncApplier applier;

    private final Map<Long, Department> deptTable = new HashMap<>();
    private final Map<Long, SysUser> userTable = new HashMap<>();
    private final List<UserDepartment> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        departmentMapper = mock(DepartmentMapper.class);
        userMapper = mock(SysUserMapper.class);
        userDepartmentMapper = mock(UserDepartmentMapper.class);

        when(departmentMapper.insert(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId((long) (deptTable.size() + 1));
            deptTable.put(d.getId(), d);
            return 1;
        });
        when(departmentMapper.selectById(anyLong())).thenAnswer(i -> deptTable.get(i.getArgument(0, Long.class)));
        when(userMapper.insert(any(SysUser.class))).thenAnswer(i -> {
            SysUser u = i.getArgument(0);
            u.setId((long) (userTable.size() + 1));
            userTable.put(u.getId(), u);
            return 1;
        });
        when(userDepartmentMapper.selectCount(any())).thenReturn(0L);
        when(userDepartmentMapper.insert(any(UserDepartment.class))).thenAnswer(i -> {
            links.add(i.getArgument(0));
            return 1;
        });
        // 默认「库里没有任何外部来源数据」，各用例按需覆盖
        when(departmentMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectList(any())).thenReturn(List.of());

        applier = new OrgSyncApplier(departmentMapper, userMapper, userDepartmentMapper);
    }

    @Test
    void 新部门与新用户按统计口径落库_父部门后置回填() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        var result = applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("D2", "D1", "研发部", 2), new OrgDept("D1", null, "总部", 1)),
                List.of(user("U1", "zhangsan", "张三", "D2", List.of("D2")))));

        assertEquals(2, result.deptTotal());
        assertEquals(2, result.deptInserted());
        assertEquals(0, result.deptUpdated());
        assertEquals(1, result.userTotal());
        assertEquals(1, result.userInserted());
        assertEquals(0, result.userSkipped());

        // 输入故意「子在前、父在后」：两遍处理必须仍能按 corpId 映射回填父级
        Department dev = byName("研发部");
        Department head = byName("总部");
        assertEquals(head.getId(), dev.getParentId(), "子部门要指向父部门 id（第二遍回填）");
        assertEquals("CASDOOR", head.getExternalSource());

        SysUser created = userTable.get(1L);
        assertEquals("zhangsan", created.getUsername());
        assertEquals("张三", created.getName());
        assertEquals("COMMON", created.getRoleCode());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(dev.getId(), created.getDeptId(), "主部门取 primaryDeptExternalId");
    }

    @Test
    void 已存在的部门与用户只做更新() {
        Department existingDept = new Department();
        existingDept.setId(10L);
        existingDept.setCorpId("D1");
        existingDept.setName("旧名");
        existingDept.setSort(9);
        SysUser existingUser = new SysUser();
        existingUser.setId(20L);
        existingUser.setExternalId("U1");
        existingUser.setName("旧名");
        when(departmentMapper.selectOne(any())).thenReturn(existingDept);
        when(userMapper.selectOne(any())).thenReturn(existingUser);

        var result = applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("D1", null, "新名", null)),
                List.of(user("U1", "zhangsan", "新人", null, List.of()))));

        assertEquals(0, result.deptInserted());
        assertEquals(1, result.deptUpdated());
        assertEquals(0, result.userInserted());
        assertEquals(1, result.userUpdated());
        assertEquals("新名", existingDept.getName());
        assertEquals(9, existingDept.getSort(), "没给 sort 时保留原排序");
        assertEquals("新人", existingUser.getName());
        assertEquals("CASDOOR", existingUser.getExternalSource());
    }

    @Test
    void 缺外部标识的部门与用户被跳过() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        var result = applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("  ", null, "无名部门", 1),
                        new OrgDept("D1", null, "总部", 1)),
                List.of(user(null, "ghost", null, null, null),
                        user("U1", "zhangsan", "张三", null, null))));

        assertEquals(1, result.deptTotal(), "空 externalId 的部门不落库也不计数");
        assertEquals(1, result.userTotal());
        assertEquals(1, result.userSkipped(), "缺标识的用户计入 skipped");
        assertTrue(deptTable.values().stream().noneMatch(d -> "无名部门".equals(d.getName())));
    }

    @Test
    void 用户部门关系先清后建_主部门标记为1() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        // 顺序敏感：写库后需要能按 externalId 再查回来（第 3 步重建关系依赖这次查询）
        when(userMapper.selectOne(any())).thenAnswer(i -> userTable.values().stream()
                .filter(u -> "U1".equals(u.getExternalId()))
                .findFirst().orElse(null));

        applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("D1", null, "总部", 1), new OrgDept("D2", null, "研发部", 2)),
                List.of(user("U1", "zhangsan", "张三", "D2", List.of("D1", "D2")))));

        verify(userDepartmentMapper).delete(any());
        assertEquals(2, links.size(), "两个部门都要建关系");
        assertTrue(links.stream().anyMatch(l -> l.getDeptId() == 1L && l.getIsPrimary() == 0));
        assertTrue(links.stream().anyMatch(l -> l.getDeptId() == 2L && l.getIsPrimary() == 1));
    }

    @Test
    void 外部源已删除的用户置DISABLED_部门删除() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        SysUser goneUser = new SysUser();
        goneUser.setId(30L);
        goneUser.setExternalId("U-GONE");
        goneUser.setStatus("ACTIVE");
        SysUser keptUser = new SysUser();
        keptUser.setId(31L);
        keptUser.setExternalId("U1");
        keptUser.setStatus("ACTIVE");
        when(userMapper.selectList(any())).thenReturn(List.of(goneUser, keptUser));

        Department goneDept = new Department();
        goneDept.setId(40L);
        goneDept.setCorpId("D-GONE");
        when(departmentMapper.selectList(any())).thenReturn(List.of(goneDept));

        applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("D1", null, "总部", 1)),
                List.of(user("U1", "zhangsan", "张三", null, List.of()))));

        assertEquals("DISABLED", goneUser.getStatus());
        assertEquals("ACTIVE", keptUser.getStatus(), "本次拉取到的用户不应被禁用");
        verify(departmentMapper).deleteById(40L);
    }

    @Test
    void 拉取为空时不触发清理_防误清空库() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        var result = applier.apply(new OrgPullData("CASDOOR", List.of(), List.of()));

        assertEquals(0, result.deptTotal());
        assertEquals(0, result.userTotal());
        verify(userMapper, never()).selectList(any());
        verify(departmentMapper, never()).selectList(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    void 已禁用的用户不重复更新() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        SysUser already = new SysUser();
        already.setId(50L);
        already.setExternalId("U-OLD");
        already.setStatus("DISABLED");
        when(userMapper.selectList(any())).thenReturn(List.of(already));

        applier.apply(new OrgPullData("CASDOOR", List.of(),
                List.of(user("U1", "zhangsan", "张三", null, List.of()))));

        verify(userMapper, times(0)).updateById(any(SysUser.class));
    }

    @Test
    void 重复关系已存在时不重复插入() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userDepartmentMapper.selectCount(any())).thenReturn(1L);

        applier.apply(new OrgPullData("CASDOOR",
                List.of(new OrgDept("D1", null, "总部", 1)),
                List.of(user("U1", "zhangsan", "张三", "D1", List.of("D1")))));

        assertTrue(links.isEmpty(), "关系已存在时 linkUserDept 直接返回");
    }

    private OrgDept.OrgUser user(String externalId, String username, String name,
                                 String primaryDept, List<String> deptIds) {
        return new OrgDept.OrgUser(externalId, username, name, "13800000000",
                "u@example.com", null, null, null, deptIds, primaryDept);
    }

    private Department byName(String name) {
        return deptTable.values().stream()
                .filter(d -> name.equals(d.getName()))
                .findFirst()
                .orElseThrow();
    }
}
