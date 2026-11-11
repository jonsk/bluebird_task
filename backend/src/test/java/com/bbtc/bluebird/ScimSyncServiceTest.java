package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.application.OrgDirectorySupport;
import com.bbtc.bluebird.modules.org.application.ScimSyncService;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCIM 模式映射：标识回落顺序、姓名拼接、active→状态、组成员关系（externalId 优先、用户名兜底）。
 *
 * <p>这些规则是接 Casdoor 时的实际踩坑点，改动必须能从断言里看出来。
 */
class ScimSyncServiceTest {

    private DepartmentMapper departmentMapper;
    private SysUserMapper userMapper;
    private UserDepartmentMapper userDepartmentMapper;
    private ConfigService config;
    private ScimSyncService service;

    private final Map<Long, Department> deptTable = new HashMap<>();
    private final Map<Long, SysUser> userTable = new HashMap<>();
    private final List<UserDepartment> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        departmentMapper = mock(DepartmentMapper.class);
        userMapper = mock(SysUserMapper.class);
        userDepartmentMapper = mock(UserDepartmentMapper.class);
        config = mock(ConfigService.class);
        when(config.orgSyncMode()).thenReturn("SCIM");

        when(departmentMapper.insert(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId((long) (deptTable.size() + 1));
            deptTable.put(d.getId(), d);
            return 1;
        });
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

        service = new ScimSyncService(departmentMapper, userMapper,
                new OrgDirectorySupport(departmentMapper, userMapper, userDepartmentMapper, config));
    }

    @AfterEach
    void tearDown() {
        OrgSyncState.setRunning(false);
    }

    @Test
    void 用户缺少所有标识时报参数错误() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upsertUser(Map.of("name", Map.of("givenName", "x"))));

        assertEquals(ErrorCode.PARAM_ERROR, e.getCode());
    }

    @Test
    void 新建用户_姓名拼接与禁用状态() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.upsertUser(Map.of(
                "externalId", "U-1",
                "userName", "zhangsan",
                "name", Map.of("familyName", "张", "givenName", "三"),
                "active", Boolean.FALSE));

        SysUser created = userTable.get(1L);
        assertEquals("张三", created.getName());
        assertEquals("DISABLED", created.getStatus());
        assertEquals("U-1", created.getExternalId());
        assertEquals("zhangsan", created.getUsername());
        assertEquals("COMMON", created.getRoleCode());
    }

    @Test
    void 用户无externalId时回落到id() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.upsertUser(Map.of("id", "U-9", "userName", "lisi"));

        SysUser created = userTable.get(1L);
        assertEquals("U-9", created.getExternalId());
        assertEquals("lisi", created.getUsername());
        assertEquals("lisi", created.getName(), "姓名缺省用用户名，再缺省用标识");
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    void active为true时启用已有用户() {
        SysUser exist = new SysUser();
        exist.setId(3L);
        exist.setStatus("DISABLED");
        when(userMapper.selectOne(any())).thenReturn(exist);

        service.upsertUser(Map.of("id", "U-3", "active", Boolean.TRUE));

        assertEquals("ACTIVE", exist.getStatus());
        verify(userMapper).updateById(exist);
    }

    @Test
    void 组标识与名称都缺失时报参数错误() {
        // displayName 缺省回落到 externalId，所以两者全空才算非法。
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upsertGroup(Map.of("schemas", List.of("core:2.0:Group"))));

        assertEquals(ErrorCode.PARAM_ERROR, e.getCode());
    }

    @Test
    void 组缺displayName时用externalId兜底命名() {
        when(departmentMapper.selectOne(any())).thenReturn(null);

        service.upsertGroup(Map.of("externalId", "G-2"));

        Department dept = deptTable.get(1L);
        assertEquals("G-2", dept.getName());
        assertEquals("G-2", dept.getCorpId());
        assertEquals(0, dept.getSort());
    }

    @Test
    void 组成员按externalId关联部门() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        SysUser member = new SysUser();
        member.setId(5L);
        when(userMapper.selectOne(any())).thenReturn(member);

        service.upsertGroup(Map.of(
                "externalId", "G-1",
                "displayName", "研发部",
                "members", List.of(Map.of("value", "U-5"), Map.of())));

        assertEquals(1, links.size());
        assertEquals(5L, links.get(0).getUserId());
        assertEquals(1L, links.get(0).getDeptId());
        assertEquals(0, links.get(0).getIsPrimary(), "组成员关系不是主部门");
    }

    @Test
    void 组成员externalId查不到时按用户名兜底() {
        when(departmentMapper.selectOne(any())).thenReturn(null);
        // 第一次按 externalId 查返回 null，第二次按用户名查到
        when(userMapper.selectOne(any())).thenReturn(null);
        SysUser byName = new SysUser();
        byName.setId(6L);
        when(userMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(byName);

        service.upsertGroup(Map.of("externalId", "G-3", "displayName", "行政部",
                "members", List.of(Map.of("value", "wangwu"))));

        assertEquals(1, links.size());
        assertEquals(6L, links.get(0).getUserId());
    }

    @Test
    void 组已存在时只更新名称() {
        Department exist = new Department();
        exist.setId(4L);
        exist.setCorpId("G-4");
        exist.setName("旧部门");
        when(departmentMapper.selectOne(any())).thenReturn(exist);

        service.upsertGroup(Map.of("externalId", "G-4", "displayName", "新部门"));

        assertEquals("新部门", exist.getName());
        verify(departmentMapper).updateById(exist);
        assertTrue(deptTable.isEmpty(), "已存在的组不应再 insert");
    }

    @Test
    void 模式不是SCIM时拒绝写入() {
        when(config.orgSyncMode()).thenReturn("NONE");

        assertThrows(BusinessException.class, () -> service.upsertUser(Map.of("id", "U-1")));
        verify(userMapper, never()).insert(any(SysUser.class));
    }
}
