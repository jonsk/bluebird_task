package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.application.OrgDirectorySupport;
import com.bbtc.bluebird.modules.org.application.OrgPushService;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.dto.OrgPushPayload;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PUSH 模式落库：两遍部门处理、用户 upsert、负责人回填、关系表幂等、删除标记。
 *
 * <p>处理顺序是这段代码的核心不变量（父部门 → 用户 → 负责人），断言全部围绕顺序造成的依赖。
 */
class OrgPushServiceTest {

    private DepartmentMapper departmentMapper;
    private SysUserMapper userMapper;
    private UserDepartmentMapper userDepartmentMapper;
    private ConfigService config;
    private OrgPushService service;

    private final Map<Long, Department> deptTable = new HashMap<>();
    private final Map<Long, SysUser> userTable = new HashMap<>();
    private final List<UserDepartment> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        departmentMapper = mock(DepartmentMapper.class);
        userMapper = mock(SysUserMapper.class);
        userDepartmentMapper = mock(UserDepartmentMapper.class);
        config = mock(ConfigService.class);
        when(config.orgSyncMode()).thenReturn("PUSH");

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

        service = new OrgPushService(departmentMapper, userMapper,
                new OrgDirectorySupport(departmentMapper, userMapper, userDepartmentMapper, config));
    }

    @AfterEach
    void tearDown() {
        OrgSyncState.setRunning(false);
    }

    @Test
    void 新建部门与用户_回填父部门负责人与关系() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        service.applyPush(new OrgPushPayload("org", "ts",
                List.of(
                        new OrgPushPayload.OrgPushDept("D1", "总部", null, "U1", 1),
                        new OrgPushPayload.OrgPushDept("D2", "研发部", "D1", null, 2)),
                List.of(new OrgPushPayload.OrgPushUser("U1", "zhangsan", "张三", "13800000000",
                        "z@example.com", "D2", List.of("D2", "D1"), "ACTIVE")),
                null));

        assertEquals(2, deptTable.size());
        Department head = deptTable.get(1L);
        Department dev = deptTable.get(2L);
        assertEquals("研发部", dev.getName());
        assertEquals(1L, dev.getParentId(), "第二遍应回填父部门");
        assertEquals(1L, head.getLeaderUserId(), "用户落库后应回填负责人");

        SysUser u = userTable.get(1L);
        assertEquals("zhangsan", u.getUsername());
        assertEquals("COMMON", u.getRoleCode());
        assertEquals(2L, u.getDeptId(), "主部门取 primaryDeptExternalId");

        assertEquals(2, links.size(), "两个部门归属都应建立关系");
        assertTrue(links.stream().anyMatch(l -> l.getIsPrimary() == 1 && l.getDeptId() == 2L));
        assertTrue(links.stream().anyMatch(l -> l.getIsPrimary() == 0 && l.getDeptId() == 1L));
    }

    @Test
    void 已存在部门只更新名称_sort为空不覆盖() {
        Department exist = new Department();
        exist.setId(7L);
        exist.setCorpId("D1");
        exist.setName("旧名");
        exist.setSort(null);
        when(departmentMapper.selectOne(any())).thenReturn(exist);

        service.applyPush(new OrgPushPayload("org", "ts",
                List.of(new OrgPushPayload.OrgPushDept("D1", "新名", null, null, null)),
                List.of(), null));

        assertEquals("新名", exist.getName());
        assertNull(exist.getSort(), "推送未带 sort 时不应把已有排序清成 null");
        verify(departmentMapper).updateById(exist);
        assertTrue(deptTable.isEmpty(), "已存在的部门不应再 insert");
    }

    @Test
    void 已存在用户只按非空字段覆盖() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        SysUser exist = new SysUser();
        exist.setId(3L);
        exist.setName("旧名");
        when(userMapper.selectOne(any())).thenReturn(exist);

        service.applyPush(new OrgPushPayload("org", "ts", List.of(),
                List.of(new OrgPushPayload.OrgPushUser("U1", "zhangsan", null, null, null, null, null, null)),
                null));

        assertEquals("旧名", exist.getName(), "推送未给姓名时保留原值");
        assertEquals("ACTIVE", exist.getStatus(), "状态缺省按 ACTIVE");
        verify(userMapper).updateById(exist);
    }

    @Test
    void 删除标记_用户置DISABLED_部门物理删() {
        SysUser deadUser = new SysUser();
        deadUser.setId(9L);
        deadUser.setStatus("ACTIVE");
        when(userMapper.selectOne(any())).thenReturn(deadUser);
        Department deadDept = new Department();
        deadDept.setId(8L);
        when(departmentMapper.selectOne(any())).thenReturn(deadDept);

        service.applyPush(new OrgPushPayload("org", "ts", List.of(), List.of(),
                new OrgPushPayload.Deleted(List.of("U-OLD"), List.of("D-OLD"))));

        assertEquals("DISABLED", deadUser.getStatus());
        verify(departmentMapper).deleteById(8L);
    }

    @Test
    void 模式不是PUSH时拒绝() {
        when(config.orgSyncMode()).thenReturn("NONE");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.applyPush(new OrgPushPayload("org", "ts", null, null, null)));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
    }

    @Test
    void 空报文不报错也不写库() {
        service.applyPush(new OrgPushPayload("org", "ts", null, null, null));

        assertTrue(deptTable.isEmpty());
        assertTrue(userTable.isEmpty());
    }
}
