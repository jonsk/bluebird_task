package com.bbtc.bluebird;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note 模块细分场景：授权下线后的可见性、附件绑定/解绑/删除守卫、标签级联与 facet、日历参数校验。
 *
 * <p>与 {@link NoteFlowTest} 互补：主流程只走一遍 happy path，这里覆盖各分支的错误码与副作用。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.security.manager-can-read-subordinate=true"
})
@AutoConfigureMockMvc
class NoteSubFlowTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-note-sub-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        try {
            String dir = Files.createTempDirectory("bb-note-sub-files").toString();
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
    TagMapper tagMapper;
    @Autowired
    TaskTagMapper taskTagMapper;
    @Autowired
    NoteAttachmentRefMapper attachmentRefMapper;
    @Autowired
    AttachmentMapper attachmentMapper;
    @Autowired
    JwtUtils jwtUtils;

    /* ==================== helpers ==================== */

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

    private String token(SysUser u) {
        return "Bearer " + jwtUtils.createAccess(u.getId(), u.getUsername(), u.getRoleCode());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, SysUser u) {
        return b.header("Authorization", token(u));
    }

    private JsonNode body(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private static final String TODAY = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();

    private long createNote(SysUser u, String content) throws Exception {
        String json = mvc.perform(auth(post("/api/v1/notes"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return body(json).path("data").path("id").asLong();
    }

    private long uploadFile(SysUser u, String name, String content) throws Exception {
        String json = mvc.perform(auth(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", name, "text/plain", content.getBytes())), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return body(json).path("data").path("id").asLong();
    }

    /* ==================== 1. 授权下线后的跨人可见性 ==================== */

    @Test
    void grantFeatureRemoved() throws Exception {
        SysUser leader = user("leader", null, "COMMON");
        Department root = dept("质量部", null, leader.getId());
        Department child = dept("质量一组", root.getId(), null);
        leader.setDeptId(root.getId());
        userMapper.updateById(leader);
        SysUser sub = user("sub", child.getId(), "COMMON");
        SysUser outsider = user("outsider", null, "COMMON");

        long subNote = createNote(sub, "下属的私有日志");

        // 1. 授权端点已下线：任何调用都是未映射 → 200 + 10004
        mvc.perform(auth(post("/api/v1/note-grants"), leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SUPERIOR\",\"grantorId\":" + sub.getId() + ",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
        mvc.perform(auth(get("/api/v1/note-grants").param("tab", "created"), leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
        mvc.perform(auth(delete("/api/v1/note-grants/1"), leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));

        // 2. scope=shared 已非法 → 400 + 10001
        mvc.perform(auth(get("/api/v1/notes").param("scope", "shared"), leader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));

        // 3. 上级可见下属日志（开关开），但只读：详情 canEdit=false、角色为空
        mvc.perform(auth(get("/api/v1/notes/" + subNote), leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEdit").value(false))
                .andExpect(jsonPath("$.data.canManage").value(false))
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.fieldPermissions.content").value(false));
        mvc.perform(auth(get("/api/v1/notes").param("scope", "subordinate"), leader))
                .andExpect(jsonPath("$.data.list[*].id").value(org.hamcrest.Matchers.hasItem(subNote)));

        // 4. 上级写入 → 403（可见但非 owner）
        mvc.perform(auth(put("/api/v1/notes/" + subNote), leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权修改\",\"version\":0}"))
                .andExpect(status().isForbidden());

        // 5. 无上下级关系的普通用户 → 404（授权能力下线后无处可授）
        mvc.perform(auth(get("/api/v1/notes/" + subNote), outsider))
                .andExpect(status().isNotFound());
    }

    /* ==================== 3. 附件绑定矩阵 ==================== */

    @Test
    void attachmentBindMatrix() throws Exception {
        SysUser leader = user("leader", null, "COMMON");
        Department root = dept("附件部", null, leader.getId());
        Department child = dept("附件组", root.getId(), null);
        leader.setDeptId(root.getId());
        userMapper.updateById(leader);
        SysUser owner = user("owner", child.getId(), "COMMON");
        SysUser stranger = user("stranger", null, "COMMON");
        long note = createNote(owner, "带附件的日志");

        long ownerFile = uploadFile(owner, "owner.txt", "owner-content");
        long viewerFile = uploadFile(leader, "leader.txt", "leader-content");

        // 不可见用户绑定 → 404（不泄露存在性）
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + ownerFile + "}"))
                .andExpect(status().isNotFound());

        // 可见但非附件创建者、非 ADMIN → 403（上级只读视角）
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + viewerFile + "}"))
                .andExpect(status().isForbidden());

        // owner 绑定 → 200；重复绑定同一附件幂等
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + ownerFile + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("/notes/" + note + "/attachments/" + ownerFile + "/download"));
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + ownerFile + "}"))
                .andExpect(status().isOk());

        // 下载：可见用户 200 且字节一致；不可见 404
        String bytes = mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments/" + ownerFile + "/download"), leader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("owner-content", bytes);
        mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments/" + ownerFile + "/download"), stranger))
                .andExpect(status().isNotFound());
        // 列表：不可见 404
        mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments"), stranger))
                .andExpect(status().isNotFound());

        // 移除关联：上级只读（canRemoveRef=false）→ 403
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/attachments/" + ownerFile), leader))
                .andExpect(status().isForbidden());
        // owner 移除 → 200，文件本身保留
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/attachments/" + ownerFile), owner))
                .andExpect(status().isOk());
        assertNotNull(attachmentMapper.selectById(ownerFile));
        assertEquals(0, attachmentRefMapper.selectCount(
                new QueryWrapper<NoteAttachmentRef>().eq("note_id", note)).intValue());
    }

    /* ==================== 4. 文件物理删除守卫 ==================== */

    @Test
    void fileDeleteGuards() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser other = user("other", null, "COMMON");
        long note = createNote(owner, "引用文件的日志");
        long file = uploadFile(owner, "ref.txt", "ref-content");

        // 非创建者读/删 → 403
        mvc.perform(auth(get("/api/v1/files/" + file), other)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/files/" + file), other)).andExpect(status().isForbidden());

        // 被 note 引用 → 物理删除 409
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + file + "}"))
                .andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/files/" + file), owner)).andExpect(status().isConflict());
        assertNotNull(attachmentMapper.selectById(file));

        // 解除引用后可物理删除
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/attachments/" + file), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/files/" + file), owner)).andExpect(status().isOk());
        assertEquals(0, attachmentMapper.selectCount(
                new QueryWrapper<com.bbtc.bluebird.modules.file.domain.Attachment>().eq("id", file)).intValue());
    }

    /* ==================== 6. 标签能力已下线 ==================== */

    @Test
    void tagFeatureRemoved() throws Exception {
        SysUser owner = user("owner", null, "COMMON");

        // 1. facet 端点已摘除：路径会命中 /notes/{id}，id 非数字 → 400 + 10001
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "mine"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));

        // 2. 创建/更新携带 tagIds 被静默忽略：不落库、不回显
        String created = mvc.perform(auth(post("/api/v1/notes"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"带 tagIds 的日志\",\"tagIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tags").doesNotExist())
                .andExpect(jsonPath("$.data.ownerTags").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long noteId = body(created).path("data").path("id").asLong();
        mvc.perform(auth(put("/api/v1/notes/" + noteId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[3],\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tags").doesNotExist());

        // 3. 列表的 tagId/tagName 参数被忽略（不再过滤）
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("tagId", "1"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("tagName", "任意"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 4. 任务侧标签删除仍会清理 task_tag（SPI 保留）
        Tag tag = new Tag();
        tag.setName("任务标签");
        tag.setOwnerId(owner.getId());
        tagMapper.insert(tag);
        TaskTag taskTag = new TaskTag();
        taskTag.setTaskId(990001L);
        taskTag.setTagId(tag.getId());
        taskTagMapper.insert(taskTag);

        mvc.perform(auth(delete("/api/v1/tags/" + tag.getId()), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(0, taskTagMapper.selectCount(
                new QueryWrapper<TaskTag>().eq("tag_id", tag.getId())).intValue());
    }

    /* ==================== 7. 列表查询校验 ==================== */

    @Test
    void queryValidation() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        createNote(owner, "查询校验日志");

        // size 上限截断为 100
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("size", "500"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        // date 与 start/end 互斥 → 400
        mvc.perform(auth(get("/api/v1/notes")
                        .param("scope", "mine").param("date", TODAY)
                        .param("start", "2026-10-01").param("end", "2026-10-31"), owner))
                .andExpect(status().isBadRequest());

        // 非法 scope → 400 + 10001
        mvc.perform(auth(get("/api/v1/notes").param("scope", "nope"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));
    }


    /* ==================== 8. 日历 scope 与校验 ==================== */

    @Test
    void calendarScopeAndValidation() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser admin = user("admin", null, "ADMIN");
        String year = TODAY.substring(0, 4);
        String month = String.valueOf(Integer.parseInt(TODAY.substring(5, 7)));

        // 日志日期不可选：三条日志都记为当天，故日历只有一天、计数为 3
        createNote(owner, "当天日志 A");
        createNote(owner, "当天日志 B");
        createNote(owner, "当天日志 C");

        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", year).param("month", month).param("scope", "mine"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].date").value(TODAY))
                .andExpect(jsonPath("$.data[0].count").value(3));

        // 非法 year → 400
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "abc").param("month", "10"), owner))
                .andExpect(status().isBadRequest());

        // scope=all 非 ADMIN → 403；ADMIN → 200
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", year).param("month", month).param("scope", "all"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", year).param("month", month).param("scope", "all"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 其它月份：该用户无日志 → 空数组
        java.time.YearMonth other = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai")).minusMonths(2);
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", String.valueOf(other.getYear()))
                        .param("month", String.valueOf(other.getMonthValue()))
                        .param("scope", "mine"), owner))
                .andExpect(jsonPath("$.data").isEmpty());

        // 客户端传 logDate 被忽略：落库仍是当天，不再报 50012
        mvc.perform(auth(post("/api/v1/notes"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"带未来日期的日志\",\"logDate\":\""
                                + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(30) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logDate").value(TODAY));
    }


}
