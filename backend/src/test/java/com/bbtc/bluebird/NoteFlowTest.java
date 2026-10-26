package com.bbtc.bluebird;

import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note 模块端到端流程：CRUD、可见性、授权、引用、附件、标签 facet、日历、级联删除。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.security.manager-can-read-subordinate=true"
})
@AutoConfigureMockMvc
class NoteFlowTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-note-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        try {
            String dir = Files.createTempDirectory("bb-note-files").toString();
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
    AttachmentMapper attachmentMapper;

    @Autowired
    JwtUtils jwtUtils;

    /* ==================== fixtures ==================== */

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
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        u.setUsername(name);
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

    private long createNote(SysUser u, String content, String logDate) throws Exception {
        String body = mvc.perform(auth(post("/api/v1/notes"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"logDate\":\"" + logDate
                                + "\",\"tagIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /* ==================== 主流程 ==================== */

    @Test
    void fullNoteFlow() throws Exception {
        SysUser owner = user("owner", null, "COMMON");
        SysUser other = user("other", null, "COMMON");
        SysUser admin = user("admin", null, "ADMIN");
        SysUser sub = user("sub", null, "COMMON");
        SysUser stranger = user("stranger", null, "COMMON");

        // owner 为某部门负责人，sub 在该部门子树内（下属可见）
        Department root = dept("研发部", null, owner.getId());
        Department child = dept("研发一组", root.getId(), null);
        owner.setDeptId(root.getId());
        userMapper.updateById(owner);
        sub.setDeptId(child.getId());
        userMapper.updateById(sub);

        // 1. 创建 PRIVATE
        String created = mvc.perform(auth(post("/api/v1/notes"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"今天完成了接口设计\",\"logDate\":\"2026-10-20\","
                                + "\"tagIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.ownerTags").isArray())
                .andReturn().getResponse().getContentAsString();
        long noteId = objectMapper.readTree(created).path("data").path("id").asLong();

        // 2. 他人不可见 → 404
        mvc.perform(auth(get("/api/v1/notes/" + noteId), other))
                .andExpect(status().isNotFound());

        // 3. owner 列表可见
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(noteId))
                .andExpect(jsonPath("$.data.list[0].contentSummary").exists())
                .andExpect(jsonPath("$.data.list[0].canEdit").value(true))
                .andExpect(jsonPath("$.data.list[0].canManage").value(true));

        // 4. 授权能力已下线：note-grants 端点返回 200 + 10004（未映射），shared scope 非法
        mvc.perform(auth(post("/api/v1/note-grants"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OWNER\",\"granteeId\":" + other.getId() + ",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
        mvc.perform(auth(get("/api/v1/notes").param("scope", "shared"), other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));

        // 5. 他人仍不可见 → 404；写入 404（不暴露存在性）
        mvc.perform(auth(get("/api/v1/notes/" + noteId), other))
                .andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/v1/notes/" + noteId), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权修改\",\"version\":0}"))
                .andExpect(status().isNotFound());

        // 6. 乐观锁：owner 用 version=0 改一次（→1），旧 version 再提交 → 10006
        mvc.perform(auth(put("/api/v1/notes/" + noteId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一次修改\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(auth(put("/api/v1/notes/" + noteId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"旧版本覆盖\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));

        // 7. 标签 + facet
        Tag tag = new Tag();
        tag.setName("周报");
        tag.setOwnerId(owner.getId());
        tagMapper.insert(tag);
        mvc.perform(auth(put("/api/v1/notes/" + noteId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[" + tag.getId() + "],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].name").value("周报"));
        mvc.perform(auth(get("/api/v1/notes/tags").param("scope", "mine"), owner))
                .andExpect(jsonPath("$.data[0].id").value(tag.getId()))
                .andExpect(jsonPath("$.data[0].count").value(1));

        // 8. 日历
        mvc.perform(auth(get("/api/v1/notes/calendar").param("year", "2026").param("month", "10"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-20"))
                .andExpect(jsonPath("$.data[0].count").value(1));

        // 9. 引用（note → note，幂等）
        long note2 = createNote(owner, "被引用的日志", "2026-10-21");
        String rel1 = mvc.perform(auth(post("/api/v1/notes/" + noteId + "/relations"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"NOTE\",\"targetId\":" + note2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("NOTE"))
                .andReturn().getResponse().getContentAsString();
        long relationId = objectMapper.readTree(rel1).path("data").path("id").asLong();
        mvc.perform(auth(post("/api/v1/notes/" + noteId + "/relations"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"NOTE\",\"targetId\":" + note2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(relationId));
        // 自引用 → 400
        mvc.perform(auth(post("/api/v1/notes/" + noteId + "/relations"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"NOTE\",\"targetId\":" + noteId + "}"))
                .andExpect(status().isBadRequest());
        // backlinks
        mvc.perform(auth(get("/api/v1/notes/" + note2 + "/backlinks"), owner))
                .andExpect(jsonPath("$.data[0].sourceNoteId").value(noteId));
        // 不可见 note 的 backlinks → 404
        mvc.perform(auth(get("/api/v1/notes/" + note2 + "/backlinks"), stranger))
                .andExpect(status().isNotFound());

        // 10. 附件：通用上传（taskId 禁止）→ 上传 → 关联 → 列表 → 下载 → 移除
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain",
                "hello-note".getBytes());
        mvc.perform(auth(multipart("/api/v1/files").file(file).param("taskId", "1"), owner))
                .andExpect(status().isBadRequest());
        String uploaded = mvc.perform(auth(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", "hello-note".getBytes())), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value(org.hamcrest.Matchers.startsWith("/files/")))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = objectMapper.readTree(uploaded).path("data").path("id").asLong();

        // 他人不可见 → 绑定附件 404（不暴露存在性；授权下线后不再有"可见但无绑定权"的角色）
        mvc.perform(auth(post("/api/v1/notes/" + noteId + "/attachments"), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + attachmentId + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/v1/notes/" + noteId + "/attachments"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attachmentId\":" + attachmentId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentId").value(attachmentId))
                .andExpect(jsonPath("$.data.canRemoveRef").value(true));
        mvc.perform(auth(get("/api/v1/notes/" + noteId + "/attachments/" + attachmentId + "/download"), owner))
                .andExpect(status().isOk());
        // 通用 /files/{id} 仅创建者/ADMIN
        mvc.perform(auth(get("/api/v1/files/" + attachmentId), other))
                .andExpect(status().isForbidden());
        // 被 note 引用 → 物理删除 409
        mvc.perform(auth(delete("/api/v1/files/" + attachmentId), owner))
                .andExpect(status().isConflict());
        // 移除关联后仍保留文件
        mvc.perform(auth(delete("/api/v1/notes/" + noteId + "/attachments/" + attachmentId), owner))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotNull(attachmentMapper.selectById(attachmentId));

        // 11. 下属可见（开关开启）：sub 的 PRIVATE 日志 owner 可读
        long subNote = createNote(sub, "下属的工作日志", "2026-10-22");
        mvc.perform(auth(get("/api/v1/notes/" + subNote), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notes").param("scope", "subordinate"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[*].id", hasItem(subNote)));

        // 12. scope=all 非 ADMIN → 403；ADMIN → 200
        mvc.perform(auth(get("/api/v1/notes").param("scope", "all"), other))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/notes").param("scope", "all"), admin))
                .andExpect(status().isOk());

        // 13. 归档能力已下线：端点不存在（未知 API 路径统一 200 + 10004），列表忽略 archived 参数
        mvc.perform(auth(post("/api/v1/notes/" + noteId + "/archive"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("archived", "true"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 14. 级联删除：删 note1 后引用/标签/附件关联清空，note2 保留
        mvc.perform(auth(delete("/api/v1/notes/" + noteId), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notes/" + noteId), owner))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/notes/" + note2), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notes/" + note2 + "/backlinks"), owner))
                .andExpect(jsonPath("$.data").isEmpty());
        // 他人自始至终不可见（授权能力已下线）
        mvc.perform(auth(get("/api/v1/notes/" + note2), other))
                .andExpect(status().isNotFound());
    }
}
