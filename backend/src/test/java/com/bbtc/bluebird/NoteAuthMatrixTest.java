package com.bbtc.bluebird;

import com.bbtc.bluebird.common.model.LoginUser;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.application.NoteAuthService;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 侧可见性等值矩阵（二轮减法后）：owner / 下属开关 / ADMIN / deleted。
 *
 * <p>日志没有授权、没有 visibility（部门可见、公开）、没有 archived，
 * 矩阵与 {@code NoteVisibilitySqlBuilder.visiblePredicate} 逐分支对齐。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.security.manager-can-read-subordinate=true"
})
class NoteAuthMatrixTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-note-auth-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    NoteAuthService authService;

    @Autowired
    SysUserMapper userMapper;

    @Autowired
    DepartmentMapper departmentMapper;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(authService, "managerCanReadSubordinate", true);
    }

    private Department dept(String name, Long parentId, Long leaderId) {
        Department d = new Department();
        d.setName(name);
        d.setParentId(parentId);
        d.setLeaderUserId(leaderId);
        d.setSort(0);
        d.setIsSystem(0);
        d.setDeleted(0);
        departmentMapper.insert(d);
        return d;
    }

    private SysUser user(String prefix, Long deptId, String role) {
        SysUser u = new SysUser();
        u.setUsername(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        u.setName(prefix);
        u.setDeptId(deptId);
        u.setRoleCode(role);
        u.setStatus("ACTIVE");
        u.setDeleted(0);
        userMapper.insert(u);
        return u;
    }

    private void as(SysUser u) {
        LoginUser lu = new LoginUser(u.getId(), u.getUsername(), u.getName(), u.getDeptId(),
                u.getRoleCode(), List.of("ROLE_" + u.getRoleCode()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(lu, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRoleCode()))));
    }

    private Note note(Long ownerId, Long deptId, int deleted) {
        Note n = new Note();
        n.setId(1L);
        n.setContent("x");
        n.setLogDate("2026-10-20");
        n.setOwnerId(ownerId);
        n.setDeptId(deptId);
        n.setDeleted(deleted);
        n.setVersion(0);
        return n;
    }

    @Test
    void visibilityMatrix() {
        SysUser leader = user("leader", null, "COMMON");
        Department root = dept("总部", null, leader.getId());
        Department child = dept("分部", root.getId(), null);
        leader.setDeptId(root.getId());
        userMapper.updateById(leader);

        SysUser parent = user("parent", root.getId(), "COMMON");
        SysUser childUser = user("child", child.getId(), "COMMON");
        SysUser outsider = user("outsider", null, "COMMON");
        SysUser admin = user("admin", null, "ADMIN");

        // 1. owner 自己的日志
        as(parent);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), 0), parent.getId()));

        // 2. 他人（跨部门、非上下级）→ false
        as(outsider);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), 0), outsider.getId()));

        // 3. 授权能力已下线：曾有授权关系也不可见（表已不存在，无分支可走）
        SysUser editor = user("editor", null, "COMMON");
        as(editor);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), 0), editor.getId()));

        // 4. 同部门但非上下级 → false（部门可见已下线）
        as(childUser);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), 0), childUser.getId()));

        // 6. 上级读下属（开关开）
        as(leader);
        assertTrue(authService.isVisible(note(childUser.getId(), child.getId(), 0), leader.getId()));

        // 7. 上级读下属（开关关）→ false
        ReflectionTestUtils.setField(authService, "managerCanReadSubordinate", false);
        assertFalse(authService.isVisible(note(childUser.getId(), child.getId(), 0), leader.getId()));
        ReflectionTestUtils.setField(authService, "managerCanReadSubordinate", true);

        // 8. 下属读上级 → false（方向单向）
        as(childUser);
        assertFalse(authService.isVisible(note(leader.getId(), root.getId(), 0), childUser.getId()));

        // 9. ADMIN 全量（含他人日志）
        as(admin);
        assertTrue(authService.isVisible(note(outsider.getId(), null, 0), admin.getId()));

        // 10. deleted=1（含 owner）→ false
        as(parent);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), 1), parent.getId()));
    }
}
