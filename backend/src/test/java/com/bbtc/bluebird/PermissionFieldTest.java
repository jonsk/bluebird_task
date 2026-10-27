package com.bbtc.bluebird;

import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限细化回归：
 * <ol>
 *   <li>note 详情下发 {@code fieldPermissions}（owner / ADMIN / 上级只读 三种身份）；</li>
 *   <li>task 详情对不可见调用方返回 <b>HTTP 404</b>（不再 200 + 10003，也不暴露存在性）；</li>
 *   <li>task 详情子任务按可见性过滤。</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.security.manager-can-read-subordinate=true"
})
@AutoConfigureMockMvc
class PermissionFieldTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-perm-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        try {
            String dir = Files.createTempDirectory("bb-perm-files").toString();
            registry.add("app.storage.root", () -> dir);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SysUserMapper userMapper;
    @Autowired
    DepartmentMapper departmentMapper;
    @Autowired
    JwtUtils jwtUtils;

    /* ==================== helpers ==================== */

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

    private Department dept(String name, Long parentId, Long leaderId) {
        Department d = new Department();
        d.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        d.setParentId(parentId);
        d.setLeaderUserId(leaderId);
        d.setSort(0);
        d.setIsSystem(0);
        d.setDeleted(0);
        departmentMapper.insert(d);
        return d;
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, SysUser u) {
        return b.header("Authorization",
                "Bearer " + jwtUtils.createAccess(u.getId(), u.getUsername(), u.getRoleCode()));
    }

    private JsonNode json(String s) throws Exception {
        return objectMapper.readTree(s);
    }

    private long createNote(SysUser u, String content) throws Exception {
        String res = mvc.perform(auth(post("/api/v1/notes"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json(res).path("data").path("id").asLong();
    }

    private long createTask(SysUser u, String title, Long parentId) throws Exception {
        String payload = "{\"title\":\"" + title + "\"" + (parentId != null ? ",\"parentId\":" + parentId : "") + "}";
        String res = mvc.perform(auth(post("/api/v1/tasks"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json(res).path("data").asLong();
    }

    /* ==================== 1. note fieldPermissions ==================== */

    @Test
    void noteDetailExposesFieldPermissions() throws Exception {
        SysUser leader = user("fp-leader", null, "COMMON");
        Department root = dept("权限部", null, leader.getId());
        Department child = dept("权限组", root.getId(), null);
        leader.setDeptId(root.getId());
        userMapper.updateById(leader);
        SysUser owner = user("fp-owner", child.getId(), "COMMON");
        SysUser admin = user("fp-admin", null, "ADMIN");

        long noteId = createNote(owner, "字段权限用例");

        // owner：正文可编辑（日期/标签已下线，位不再下发）
        mvc.perform(auth(get("/api/v1/notes/" + noteId), owner))
                .andExpect(jsonPath("$.data.fieldPermissions.content").value(true))
                .andExpect(jsonPath("$.data.fieldPermissions.logDate").doesNotExist())
                .andExpect(jsonPath("$.data.fieldPermissions.tagIds").doesNotExist());

        // ADMIN：正文可编辑（与 canManage 一致）
        mvc.perform(auth(get("/api/v1/notes/" + noteId), admin))
                .andExpect(jsonPath("$.data.fieldPermissions.content").value(true));

        // 上级只读：全 false，写入 403（授权/协作编辑已下线）
        mvc.perform(auth(get("/api/v1/notes/" + noteId), leader))
                .andExpect(jsonPath("$.data.fieldPermissions.content").value(false))
                .andExpect(jsonPath("$.data.canEdit").value(false));
        mvc.perform(auth(put("/api/v1/notes/" + noteId), leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权修改\",\"version\":0}"))
                .andExpect(status().isForbidden());

        // 无关用户：404
        SysUser other = user("fp-other", null, "COMMON");
        mvc.perform(auth(get("/api/v1/notes/" + noteId), other))
                .andExpect(status().isNotFound());
    }

    /* ==================== 2. task 详情不可见 → 404 ==================== */

    @Test
    void taskDetailIs404WhenNotVisible() throws Exception {
        SysUser owner = user("td-owner", null, "COMMON");
        SysUser other = user("td-other", null, "COMMON");
        SysUser admin = user("td-admin", null, "ADMIN");

        long taskId = createTask(owner, "私有任务", null);

        // 无关用户：404（不是 403/200，也不暴露存在性）
        mvc.perform(auth(get("/api/v1/tasks/" + taskId), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10004));

        // 不存在的 id：同样 404（与不可见不可区分）
        mvc.perform(auth(get("/api/v1/tasks/999999999"), other))
                .andExpect(status().isNotFound());

        // owner 与 ADMIN 正常
        mvc.perform(auth(get("/api/v1/tasks/" + taskId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(auth(get("/api/v1/tasks/" + taskId), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 3. 子任务随父任务可见 / 单独访问仍需可见 ==================== */

    @Test
    void subtasksFollowParentVisibility() throws Exception {
        SysUser owner = user("td2-owner", null, "COMMON");
        SysUser viewer = user("td2-viewer", null, "COMMON");
        SysUser stranger = user("td2-stranger", null, "COMMON");

        // 父任务直接把 viewer 作为被关注人（CC）→ 只读可见
        String res = mvc.perform(auth(post("/api/v1/tasks"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"父任务\",\"ccIds\":[" + viewer.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long parent = json(res).path("data").asLong();
        long child = createTask(owner, "子任务-可见", parent);

        // CC 可见父任务 → 子任务随父任务返回
        mvc.perform(auth(get("/api/v1/tasks/" + parent), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtasks.length()").value(1))
                .andExpect(jsonPath("$.data.subtasks[0].title").value("子任务-可见"));

        // 但无关用户直接按子任务 id 访问 → 404（不因「父任务存在」泄漏）
        mvc.perform(auth(get("/api/v1/tasks/" + child), stranger))
                .andExpect(status().isNotFound());
    }
}
