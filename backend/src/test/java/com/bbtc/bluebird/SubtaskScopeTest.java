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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 步骤（子任务）隔离回归测试。
 *
 * <p>用户反馈 BUG：一个任务里加多个「步骤」后，每个步骤都变成了一条独立任务。
 * 根因是六大视图的列表查询没有排除 `parent_id IS NOT NULL` 的行——步骤与父任务同表，
 * 于是每加一步，视图与计数里就多出一条任务。旧系统步骤存于独立子表，
 * 天然不会混进 `task_record` 的列表查询，故步骤**只应出现在父任务详情的 subtasks 中**。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class SubtaskScopeTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-subtask-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String PARENT = "父任务-步骤隔离";
    private static final String STEP_A = "步骤A";
    private static final String STEP_B = "步骤B";

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Test123456\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private long create(String token, String title, Long parentId) throws Exception {
        String payload = "{\"title\":\"" + title + "\""
                + (parentId == null ? "" : ",\"parentId\":" + parentId) + "}";
        String created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(created).path("data").asLong();
    }

    private JsonNode api(String token, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private List<String> titles(String token, String url) throws Exception {
        List<String> out = new ArrayList<>();
        api(token, url).path("list").forEach(n -> out.add(n.path("title").asText()));
        return out;
    }

    @Test
    void stepsNeverAppearAsStandaloneTasks() throws Exception {
        String token = login();
        long parent = create(token, PARENT, null);
        long stepA = create(token, STEP_A, parent);
        long stepB = create(token, STEP_B, parent);
        assertTrue(stepA > 0 && stepB > 0);

        // 1) 六大视图都不应出现步骤
        for (String scope : List.of("day", "week", "joined", "assigned", "collect", "all")) {
            List<String> list = titles(token, "/api/v1/tasks?scope=" + scope + "&size=100");
            assertFalse(list.contains(STEP_A), scope + " 视图不应包含步骤：" + list);
            assertFalse(list.contains(STEP_B), scope + " 视图不应包含步骤：" + list);
        }
        // 父任务本身仍应出现（避免「把父任务也一起过滤掉了」这种过度修复）
        assertTrue(titles(token, "/api/v1/tasks?scope=all&size=100").contains(PARENT), "父任务必须仍在「全部任务」中");
        assertTrue(titles(token, "/api/v1/tasks?scope=day&size=100").contains(PARENT), "父任务必须仍在「我的一天」中");

        // 2) 计数不应把步骤算进去
        JsonNode counts = api(token, "/api/v1/tasks/count");
        assertEquals(1, counts.path("day").asInt(), "day 计数只应有父任务：" + counts);
        assertEquals(1, counts.path("all").asInt(), "all 计数只应有父任务：" + counts);

        // 3) 日历不应把步骤算进去
        String start = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String end = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        List<String> cal = titles(token, "/api/v1/tasks/calendar?start=" + start + "&end=" + end);
        assertFalse(cal.contains(STEP_A), "日历不应包含步骤：" + cal);
        assertFalse(cal.contains(STEP_B), "日历不应包含步骤：" + cal);
        assertTrue(cal.contains(PARENT), "日历应包含父任务：" + cal);

        // 4) 步骤必须能在父任务详情的 subtasks 里看到
        JsonNode detail = api(token, "/api/v1/tasks/" + parent);
        List<String> subs = new ArrayList<>();
        detail.path("subtasks").forEach(n -> subs.add(n.path("title").asText()));
        assertTrue(subs.contains(STEP_A) && subs.contains(STEP_B), "父任务详情应含两个步骤：" + subs);

        // 5) 子任务接口同样返回两个步骤
        List<String> viaApi = titles(token, "/api/v1/tasks/subtasks?parentId=" + parent);
        assertTrue(viaApi.contains(STEP_A) && viaApi.contains(STEP_B), "子任务接口应含两个步骤：" + viaApi);
    }
}
