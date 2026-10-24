package com.bbtc.bluebird;

import com.bbtc.bluebird.common.model.LoginUser;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.application.NoteAuthService;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import com.bbtc.bluebird.modules.note.infrastructure.NoteUserGrantMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §6.3 Java 侧可见性等值矩阵（15 行）：Grant EDITOR、deleted=1、空 visibleDeptIds 均覆盖。
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

    @Autowired
    NoteUserGrantMapper grantMapper;

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

    private Note note(Long ownerId, Long deptId, String visibility, int archived, int deleted) {
        Note n = new Note();
        n.setId(1L);
        n.setContent("x");
        n.setLogDate("2026-10-20");
        n.setOwnerId(ownerId);
        n.setDeptId(deptId);
        n.setVisibility(visibility);
        n.setArchived(archived);
        n.setPinned(0);
        n.setDeleted(deleted);
        n.setVersion(0);
        return n;
    }

    private void grant(Long grantorId, Long granteeId, String role) {
        NoteUserGrant g = new NoteUserGrant();
        g.setGrantorId(grantorId);
        g.setGranteeId(granteeId);
        g.setRole(role);
        g.setStatus(NoteUserGrant.APPROVED);
        g.setMode(NoteUserGrant.MODE_OWNER);
        g.setCreatedAt(Instant.now());
        g.setCreatedBy(grantorId);
        g.setApprovedAt(Instant.now());
        g.setApprovedBy(grantorId);
        grantMapper.insert(g);
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

        // 1. owner 自己的 PRIVATE
        as(parent);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 0, 0), parent.getId()));

        // 2. 他人 PRIVATE，无 grant
        as(outsider);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 0, 0), outsider.getId()));

        // 3. grant VIEWER
        grant(parent.getId(), outsider.getId(), NoteUserGrant.VIEWER);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 0, 0), outsider.getId()));

        // 4. grant EDITOR（另一用户，避免与 3 冲突）
        SysUser editor = user("editor", null, "COMMON");
        grant(parent.getId(), editor.getId(), NoteUserGrant.EDITOR);
        as(editor);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 0, 0), editor.getId()));

        // 5. INTERNAL 父看子
        as(parent);
        assertTrue(authService.isVisible(note(childUser.getId(), child.getId(), "INTERNAL", 0, 0), parent.getId()));

        // 6. INTERNAL 子看父
        as(childUser);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), "INTERNAL", 0, 0), childUser.getId()));

        // 7. PUBLIC
        as(outsider);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PUBLIC", 0, 0), outsider.getId()));

        // 8. 下属开关开（他名下属，PRIVATE）
        as(leader);
        assertTrue(authService.isVisible(note(childUser.getId(), child.getId(), "PRIVATE", 0, 0), leader.getId()));

        // 9. 下属开关关
        ReflectionTestUtils.setField(authService, "managerCanReadSubordinate", false);
        assertFalse(authService.isVisible(note(childUser.getId(), child.getId(), "PRIVATE", 0, 0), leader.getId()));
        ReflectionTestUtils.setField(authService, "managerCanReadSubordinate", true);

        // 10. ADMIN 全量（含他人 PRIVATE）
        as(admin);
        assertTrue(authService.isVisible(note(outsider.getId(), null, "PRIVATE", 0, 0), admin.getId()));

        // 11. visibleDeptIds 为空 + INTERNAL → false
        SysUser noDept = user("nodept", null, "COMMON");
        as(noDept);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), "INTERNAL", 0, 0), noDept.getId()));

        // 12. visibleDeptIds 为空 + PUBLIC → true
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PUBLIC", 0, 0), noDept.getId()));

        // 13. deleted=1（含 owner）→ false
        as(parent);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 0, 1), parent.getId()));

        // 14. 已归档、非 owner → false
        as(outsider);
        assertFalse(authService.isVisible(note(parent.getId(), root.getId(), "PUBLIC", 1, 0), outsider.getId()));

        // 15. 已归档、owner / admin → true（isVisible 原始语义；列表 visible 另叠加 archived=0）
        as(parent);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 1, 0), parent.getId()));
        as(admin);
        assertTrue(authService.isVisible(note(parent.getId(), root.getId(), "PRIVATE", 1, 0), admin.getId()));
    }
}
