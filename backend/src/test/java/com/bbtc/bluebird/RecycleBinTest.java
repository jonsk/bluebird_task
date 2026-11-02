package com.bbtc.bluebird;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回收站（2026-10-30）：删除不再级联清理，恢复后关联数据仍在；彻底删除仅 ADMIN；只看自己删的。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class RecycleBinTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-recycle-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper departmentMapper;

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private long createTask(String token, String title) throws Exception {
        String body = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    /** 删除任务 → 回收站可见 → 恢复 → 自定义栏里的条目仍在（说明未级联清理）。 */
    @Test
    void taskDeleteRestoreKeepsMenuLink() throws Exception {
        String token = login("admin", "Test123456");
        long taskId = createTask(token, "回收站任务A");

        String menuBody = mvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"回收站栏\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long menuId = objectMapper.readTree(menuBody).path("data").asLong();
        mvc.perform(post("/api/v1/menus/" + menuId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(delete("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // 列表里看不到，回收站里看得到且带删除时间/删除人
        mvc.perform(get("/api/v1/recycle").param("type", "task")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].type").value("TASK"))
                .andExpect(jsonPath("$.data.list[0].id").value(taskId))
                .andExpect(jsonPath("$.data.list[0].restorable").value(true))
                .andExpect(jsonPath("$.data.list[0].deletedAt").isNotEmpty());

        // 自定义栏：任务已删时 brief 为空但条目还在（未硬删 menu_item）
        JsonNode menus = objectMapper.readTree(new String(mvc.perform(get("/api/v1/menus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        JsonNode items = menus.path("data").get(0).path("items");
        boolean found = false;
        for (JsonNode it : items) {
            if (it.path("taskId").asLong() == taskId) {
                found = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "menu_item 不应在删除任务时被硬删");

        // 恢复：任务回到列表，自定义栏条目的 brief 也回来了
        mvc.perform(post("/api/v1/recycle/tasks/" + taskId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("回收站任务A"));

        JsonNode menusAfter = objectMapper.readTree(new String(mvc.perform(get("/api/v1/menus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        JsonNode item = menusAfter.path("data").get(0).path("items").get(0);
        org.junit.jupiter.api.Assertions.assertEquals("回收站任务A", item.path("task").path("title").asText());

        mvc.perform(get("/api/v1/recycle").param("type", "task")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 日志：删除后可恢复，附件引用不被提前清掉；彻底删除后物理行消失。 */
    @Test
    void noteDeleteRestoreThenPurge() throws Exception {
        String token = login("admin", "Test123456");
        String created = mvc.perform(post("/api/v1/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"回收站日志内容\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long noteId = objectMapper.readTree(created).path("data").path("id").asLong();

        mvc.perform(delete("/api/v1/notes/" + noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/recycle").param("type", "note")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].type").value("NOTE"))
                .andExpect(jsonPath("$.data.list[0].id").value(noteId));

        mvc.perform(post("/api/v1/recycle/notes/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/notes/" + noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("回收站日志内容"));

        // 再删一次，然后彻底删除（ADMIN）
        mvc.perform(delete("/api/v1/notes/" + noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/v1/recycle/notes/" + noteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/recycle").param("type", "note")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/recycle/notes/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(70001));
    }

    /** 只看自己删的：普通用户删的任务不进 ADMIN 的回收站；普通用户也不能彻底删除（403）。 */
    @Test
    void recycleIsScopedToOwnDeletionsAndPurgeIsAdminOnly() throws Exception {
        String adminToken = login("admin", "Test123456");
        Long deptId = departmentMapper.selectList(null).get(0).getId();

        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"recycleuser\",\"name\":\"回收站用户\",\"password\":\"User123!\","
                                + "\"deptId\":" + deptId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        String userToken = login("recycleuser", "User123!");
        long userTaskId = createTask(userToken, "普通用户的任务");
        mvc.perform(delete("/api/v1/tasks/" + userTaskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(0));

        // 普通用户能看到自己删的
        mvc.perform(get("/api/v1/recycle")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(userTaskId));

        // ADMIN 看不到别人删的（回收站按删除人收窄）
        JsonNode adminList = objectMapper.readTree(mvc.perform(get("/api/v1/recycle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode it : adminList.path("data").path("list")) {
            org.junit.jupiter.api.Assertions.assertNotEquals(userTaskId, it.path("id").asLong());
        }

        // 非 ADMIN 彻底删除 → 10003（方法级鉴权异常由 GlobalExceptionHandler 统一包成 HTTP 200 + code 10003）
        mvc.perform(delete("/api/v1/recycle/tasks/" + userTaskId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(10003));

        // ADMIN 可以彻底删除任意回收站记录，物理删行后连恢复都不行
        mvc.perform(delete("/api/v1/recycle/tasks/" + userTaskId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/v1/recycle/tasks/" + userTaskId + "/restore")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(70001));
    }
}
