package com.bbtc.bluebird;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import com.bbtc.bluebird.modules.note.domain.NoteTag;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteRelationMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteTagMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note 模块细分场景：授权状态机、附件绑定/解绑/删除守卫、引用可见性、标签级联与 facet、日历参数校验。
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
    NoteTagMapper noteTagMapper;
    @Autowired
    TaskTagMapper taskTagMapper;
    @Autowired
    NoteRelationMapper noteRelationMapper;
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

    private long createNote(SysUser u, String content, String visibility, String logDate) throws Exception {
        String json = mvc.perform(auth(post("/api/v1/notes"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"logDate\":\"" + logDate
                                + "\",\"visibility\":\"" + visibility + "\",\"tagIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return body(json).path("data").path("id").asLong();
    }

    private long createGrant(SysUser creator, String bodyJson) throws Exception {
        String json = mvc.perform(auth(post("/api/v1/note-grants"), creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
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

    /* ==================== 1. 授权状态机：SUPERIOR 待确认 → 确认/拒绝 ==================== */

    @Test
    void superiorGrantApprovalFlow() throws Exception {
        SysUser leader = user("leader", null, "COMMON");
        Department root = dept("质量部", null, leader.getId());
        Department child = dept("质量一组", root.getId(), null);
        leader.setDeptId(root.getId());
        userMapper.updateById(leader);
        SysUser sub = user("sub", child.getId(), "COMMON");
        SysUser outsider = user("outsider", null, "COMMON");

        long subNote = createNote(sub, "下属的私有日志", "PRIVATE", "2026-10-10");

        // 上级发起「读下属」授权：SUPERIOR 模式 → PENDING
        long grantId = createGrant(leader, "{\"mode\":\"SUPERIOR\",\"grantorId\":" + sub.getId()
                + ",\"role\":\"VIEWER\"}");
        mvc.perform(auth(get("/api/v1/note-grants").param("tab", "created"), leader))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].canRevoke").value(true));

        // 待确认期间：授权行仍是 PENDING，且不产生 shared 可见
        mvc.perform(auth(get("/api/v1/notes").param("scope", "shared"), leader))
                .andExpect(jsonPath("$.data.total").value(0));

        // 非 grantor 确认 → 403
        mvc.perform(auth(post("/api/v1/note-grants/" + grantId + "/approve"), outsider))
                .andExpect(status().isForbidden());

        // grantor 确认 → APPROVED，上级可读、subordinate scope 命中
        mvc.perform(auth(post("/api/v1/note-grants/" + grantId + "/approve"), sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value(sub.getId()));
        mvc.perform(auth(get("/api/v1/notes/" + subNote), leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantRole").value("VIEWER"));
        mvc.perform(auth(get("/api/v1/notes").param("scope", "subordinate"), leader))
                .andExpect(jsonPath("$.data.list[*].id").value(org.hamcrest.Matchers.hasItem(subNote)));

        // 重复确认（非 PENDING）→ 50014
        mvc.perform(auth(post("/api/v1/note-grants/" + grantId + "/approve"), sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50014));

        // 另一条待确认授权：拒绝后不产生可见性
        SysUser sub2 = user("sub2", child.getId(), "COMMON");
        long sub2Note = createNote(sub2, "另一位下属的日志", "PRIVATE", "2026-10-11");
        long grant2 = createGrant(leader, "{\"mode\":\"SUPERIOR\",\"grantorId\":" + sub2.getId()
                + ",\"role\":\"VIEWER\"}");
        mvc.perform(auth(post("/api/v1/note-grants/" + grant2 + "/reject"), sub2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        // 拒绝后该授权行不生效（仍为 REJECTED，非 APPROVED）
        mvc.perform(auth(get("/api/v1/note-grants").param("tab", "created").param("status", "REJECTED"), leader))
                .andExpect(jsonPath("$.data[0].id").value(grant2));
        // 非 PENDING 再拒绝 → 50014
        mvc.perform(auth(post("/api/v1/note-grants/" + grant2 + "/reject"), sub2))
                .andExpect(jsonPath("$.code").value(50014));
    }

    /* ==================== 2. 授权变更规则：自授权/重复/升级/撤销 ==================== */

    @Test
    void grantMutationRules() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser other = user("other", null, "COMMON");
        SysUser admin = user("admin", null, "ADMIN");

        // 自授权 → 50008
        mvc.perform(auth(post("/api/v1/note-grants"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OWNER\",\"granteeId\":" + owner.getId() + ",\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code").value(50008));

        long grantId = createGrant(owner, "{\"mode\":\"OWNER\",\"granteeId\":" + other.getId()
                + ",\"role\":\"VIEWER\"}");

        // 重复授权 → 50007
        mvc.perform(auth(post("/api/v1/note-grants"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OWNER\",\"granteeId\":" + other.getId() + ",\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code").value(50007));

        // 升级 VIEWER → EDITOR
        mvc.perform(auth(post("/api/v1/note-grants/" + grantId + "/upgrade"), owner))
                .andExpect(jsonPath("$.data.role").value("EDITOR"));

        // 撤销：非 grantor 的普通用户 → 403
        SysUser stranger = user("stranger", null, "COMMON");
        mvc.perform(auth(delete("/api/v1/note-grants/" + grantId), stranger))
                .andExpect(status().isForbidden());

        // ADMIN 代撤销 → 200 且记录 approvedBy
        mvc.perform(auth(delete("/api/v1/note-grants/" + grantId), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        // 撤销后 other 立即失去访问
        long note = createNote(owner, "撤销后不可见", "PRIVATE", "2026-10-12");
        mvc.perform(auth(get("/api/v1/notes/" + note), other))
                .andExpect(status().isNotFound());

        // tab=all 仅 ADMIN
        mvc.perform(auth(get("/api/v1/note-grants").param("tab", "all"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/note-grants").param("tab", "all"), admin))
                .andExpect(status().isOk());
    }

    /* ==================== 3. 附件绑定矩阵 ==================== */

    @Test
    void attachmentBindMatrix() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser viewer = user("viewer", null, "COMMON");
        SysUser stranger = user("stranger", null, "COMMON");
        long note = createNote(owner, "带附件的日志", "PRIVATE", "2026-10-13");
        createGrant(owner, "{\"mode\":\"OWNER\",\"granteeId\":" + viewer.getId() + ",\"role\":\"VIEWER\"}");

        long ownerFile = uploadFile(owner, "owner.txt", "owner-content");
        long viewerFile = uploadFile(viewer, "viewer.txt", "viewer-content");

        // 不可见用户绑定 → 404（不泄露存在性）
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + ownerFile + "}"))
                .andExpect(status().isNotFound());

        // 可见但非附件创建者、非 ADMIN → 403
        mvc.perform(auth(post("/api/v1/notes/" + note + "/attachments"), viewer)
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
        String bytes = mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments/" + ownerFile + "/download"), viewer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("owner-content", bytes);
        mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments/" + ownerFile + "/download"), stranger))
                .andExpect(status().isNotFound());
        // 列表：不可见 404
        mvc.perform(auth(get("/api/v1/notes/" + note + "/attachments"), stranger))
                .andExpect(status().isNotFound());

        // 移除关联：VIEWER（canRemoveRef=false）→ 403
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/attachments/" + ownerFile), viewer))
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
        long note = createNote(owner, "引用文件的日志", "PRIVATE", "2026-10-14");
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

    /* ==================== 5. 引用可见性 ==================== */

    @Test
    void relationVisibilityRules() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser hidden = user("hidden", null, "COMMON");
        SysUser stranger = user("stranger", null, "COMMON");

        long note = createNote(owner, "引用方", "PRIVATE", "2026-10-15");
        long hiddenNote = createNote(hidden, "被引用但不可见", "PRIVATE", "2026-10-16");

        // 引用不可见目标 → 50006
        mvc.perform(auth(post("/api/v1/notes/" + note + "/relations"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"NOTE\",\"targetId\":" + hiddenNote + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50006));

        // 正常引用 → 列表 + backlinks
        long target = createNote(owner, "被引用方", "PRIVATE", "2026-10-17");
        String relJson = mvc.perform(auth(post("/api/v1/notes/" + note + "/relations"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"NOTE\",\"targetId\":" + target + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetId").value(target))
                .andReturn().getResponse().getContentAsString();
        long relId = body(relJson).path("data").path("id").asLong();

        mvc.perform(auth(get("/api/v1/notes/" + note + "/relations"), owner))
                .andExpect(jsonPath("$.data[0].targetId").value(target));
        mvc.perform(auth(get("/api/v1/notes/" + target + "/backlinks"), owner))
                .andExpect(jsonPath("$.data[0].sourceNoteId").value(note));

        // 不可见方读引用/被引用 → 404
        mvc.perform(auth(get("/api/v1/notes/" + note + "/relations"), stranger))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/notes/" + target + "/backlinks"), stranger))
                .andExpect(status().isNotFound());

        // 可见但非 owner（VIEWER 授权）删除引用 → 403；不可见 → 404
        SysUser viewer = user("viewer", null, "COMMON");
        createGrant(owner, "{\"mode\":\"OWNER\",\"granteeId\":" + viewer.getId() + ",\"role\":\"VIEWER\"}");
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/relations/" + relId), viewer))
                .andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/relations/" + relId), stranger))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/notes/" + note + "/relations/" + relId), owner))
                .andExpect(status().isOk());
        assertEquals(0, noteRelationMapper.selectCount(
                new QueryWrapper<NoteRelation>().eq("id", relId)).intValue());
    }

    /* ==================== 6. 标签级联清理 ==================== */

    @Test
    void tagCascadeCleanup() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        Tag tag = new Tag();
        tag.setName("级联标签");
        tag.setOwnerId(owner.getId());
        tagMapper.insert(tag);

        // note_tag 引用
        String created = mvc.perform(auth(post("/api/v1/notes"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"带标签的日志\",\"logDate\":\"2026-10-18\","
                                + "\"visibility\":\"PRIVATE\",\"tagIds\":[" + tag.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].name").value("级联标签"))
                .andReturn().getResponse().getContentAsString();
        long noteId = body(created).path("data").path("id").asLong();

        // task_tag 引用（直接落库，模拟任务侧已引用）
        TaskTag taskTag = new TaskTag();
        taskTag.setTaskId(990001L);
        taskTag.setTagId(tag.getId());
        taskTagMapper.insert(taskTag);

        mvc.perform(auth(delete("/api/v1/tags/" + tag.getId()), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(0, noteTagMapper.selectCount(
                new QueryWrapper<NoteTag>().eq("tag_id", tag.getId())).intValue());
        assertEquals(0, taskTagMapper.selectCount(
                new QueryWrapper<TaskTag>().eq("tag_id", tag.getId())).intValue());
        // 日志本身保留，标签消失
        mvc.perform(auth(get("/api/v1/notes/" + noteId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags").isEmpty());

        // 不存在/他人标签 → 200 + 10004（信息隐藏）
        mvc.perform(auth(delete("/api/v1/tags/99999999"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
        SysUser other = user("other", null, "COMMON");
        Tag otherTag = new Tag();
        otherTag.setName("他人标签");
        otherTag.setOwnerId(other.getId());
        tagMapper.insert(otherTag);
        mvc.perform(auth(delete("/api/v1/tags/" + otherTag.getId()), owner))
                .andExpect(jsonPath("$.code").value(10004));
        assertNotNull(tagMapper.selectById(otherTag.getId()));
    }

    /* ==================== 7. facet 与列表参数校验 ==================== */

    @Test
    void facetAndQueryValidation() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser stranger = user("stranger", null, "COMMON");
        long note = createNote(owner, "facet 校验日志", "PRIVATE", "2026-10-19");
        createGrant(owner, "{\"mode\":\"OWNER\",\"granteeId\":" + stranger.getId() + ",\"role\":\"VIEWER\"}");

        // mine facet 带 id；shared facet 不带 id
        Tag tag = new Tag();
        tag.setName("facet标签");
        tag.setOwnerId(owner.getId());
        tagMapper.insert(tag);
        mvc.perform(auth(put("/api/v1/notes/" + note), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[" + tag.getId() + "],\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "mine"), owner))
                .andExpect(jsonPath("$.data[0].id").value(tag.getId()));
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "shared"), stranger))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].name").value("facet标签"));

        // scope 非法 / all 非 ADMIN
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "xxx"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "all"), owner))
                .andExpect(status().isForbidden());

        // size 超上限截断为 100
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("size", "500"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        // date 与 start/end 互斥 → 400
        mvc.perform(auth(get("/api/v1/notes")
                        .param("scope", "mine").param("date", "2026-10-19")
                        .param("start", "2026-10-01").param("end", "2026-10-31"), owner))
                .andExpect(status().isBadRequest());

        // 不存在的 tagId → 空列表（不报错）
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("tagId", "999999"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // tagId 非数字 → 400
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("tagId", "abc"), owner))
                .andExpect(status().isBadRequest());
    }

    /* ==================== 8. 日历 scope 与参数校验 ==================== */

    @Test
    void calendarScopeAndValidation() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser admin = user("admin", null, "ADMIN");

        createNote(owner, "日历日志 A", "PRIVATE", "2026-10-05");
        createNote(owner, "日历日志 B", "PRIVATE", "2026-10-05");
        createNote(owner, "日历日志 C", "PRIVATE", "2026-10-06");

        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "2026").param("month", "10").param("scope", "mine"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.data[0].count").value(2))
                .andExpect(jsonPath("$.data[1].count").value(1));

        // 非法 year → 400
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "abc").param("month", "10"), owner))
                .andExpect(status().isBadRequest());

        // scope=all 非 ADMIN → 403；ADMIN → 200
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "2026").param("month", "10").param("scope", "all"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "2026").param("month", "10").param("scope", "all"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 其他月份：无日志 → 空数组
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", "2026").param("month", "11").param("scope", "mine"), owner))
                .andExpect(jsonPath("$.data").isEmpty());

        // 未来超 7 天的 logDate → 50012
        mvc.perform(auth(post("/api/v1/notes"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"未来日志\",\"logDate\":\"" + java.time.LocalDate.now().plusDays(30)
                                + "\",\"visibility\":\"PRIVATE\",\"tagIds\":[]}"))
                .andExpect(jsonPath("$.code").value(50012));

        // 无部门用户创建 INTERNAL → 50017
        SysUser noDept = user("nodept", null, "COMMON");
        mvc.perform(auth(post("/api/v1/notes"), noDept)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"部门日志\",\"logDate\":\"" + java.time.LocalDate.now()
                                + "\",\"visibility\":\"INTERNAL\",\"tagIds\":[]}"))
                .andExpect(jsonPath("$.code").value(50017));

        assertTrue(noteRelationMapper.selectCount(null) >= 0);
    }
}
