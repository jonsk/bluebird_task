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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务闭环 + 乐观锁 + 响应头/参数安全（02 §4.7 / §1.13）。 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class TaskFlowTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-task-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Test123456\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    void createListCompleteWithOptimisticLock() throws Exception {
        String token = login();

        String created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"提交季度报告\",\"content\":\"整理数据\",\"priority\":\"HIGH\","
                                + "\"dueAt\":\"2030-10-01T10:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").asLong();

        mvc.perform(get("/api/v1/tasks").param("scope", "all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].title").value("提交季度报告"));

        JsonNode detail = objectMapper.readTree(mvc.perform(get("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString());
        long version = detail.path("data").path("version").asLong();

        mvc.perform(post("/api/v1/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 过期版本 → 10006
        mvc.perform(post("/api/v1/tasks/" + id + "/uncomplete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));
    }

    @Test
    void updateRequiresVersion() throws Exception {
        String token = login();
        String created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"任务A\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").asLong();

        // 无 version → 10006
        mvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"任务A2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));

        mvc.perform(delete("/api/v1/tasks/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void noStoreHeaderAndQuerySecretGuard() throws Exception {
        String token = login();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        // query 中出现 token → 10001
        mvc.perform(get("/api/v1/users/me").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void countsEndpoint() throws Exception {
        String token = login();
        mvc.perform(get("/api/v1/tasks/count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.all").exists());
    }

    /** 分类子树过滤 + 自定义栏过滤（E-08/E-10 的服务端支撑）。 */
    @Test
    void filterByCategorySubtreeAndCustomMenu() throws Exception {
        String token = login();

        long parent = createCategory(token, "父分类", null);
        long child = createCategory(token, "子分类", parent);

        mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"归类任务\",\"categoryId\":" + child + "}"))
                .andExpect(jsonPath("$.code").value(0));

        // 按父分类过滤 → 子树（含子分类）命中
        mvc.perform(get("/api/v1/tasks").param("scope", "all").param("categoryId", String.valueOf(parent))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("归类任务"));

        // 无关分类 → 空
        long other = createCategory(token, "无关分类", null);
        mvc.perform(get("/api/v1/tasks").param("scope", "all").param("categoryId", String.valueOf(other))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));

        // 自定义栏：加条目后可过滤
        long taskId = objectMapper.readTree(mvc.perform(get("/api/v1/tasks")
                        .param("scope", "all").param("categoryId", String.valueOf(parent))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("list").get(0).path("id").asLong();

        long menuId = objectMapper.readTree(mvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"本周重点\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString()).path("data").asLong();

        mvc.perform(post("/api/v1/menus/" + menuId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/tasks").param("scope", "all").param("menuId", String.valueOf(menuId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("归类任务"));

        // 不存在/非本人自定义栏 → 10004
        mvc.perform(get("/api/v1/tasks").param("scope", "all").param("menuId", "99999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(10004));
    }

    private long createCategory(String token, String name, Long parentId) throws Exception {
        String body = "{\"name\":\"" + name + "\"" + (parentId == null ? "" : ",\"parentId\":" + parentId) + "}";
        return objectMapper.readTree(mvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString()).path("data").asLong();
    }
}
