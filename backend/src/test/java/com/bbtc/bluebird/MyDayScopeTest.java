package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
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
 * 「我的一天」口径回归测试。
 *
 * <p>旧系统 `TaskRecordServiceImpl.getDayTask` 按 `task_setup_time LIKE 'yyyy-MM-dd'` 过滤，
 * 旧库该列注释为**「任务建立时间」**——即**当天新建的任务**，与截止时间无关。
 * 本重构曾误实现为「due_at 在今天（含过期未完成）」，导致**没有截止时间的新任务
 * 永远进不了「我的一天」**；本类锁定正确口径。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class MyDayScopeTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-myday-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TaskMapper taskMapper;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Test123456\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private long create(String token, String title, String dueAtJson) throws Exception {
        String payload = "{\"title\":\"" + title + "\",\"priority\":\"NORMAL\""
                + (dueAtJson == null ? "" : ",\"dueAt\":\"" + dueAtJson + "\"") + "}";
        String created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 显式传 UTF-8 字节：MockMvc 的 content(String) 依赖请求字符集，
                        // 默认可能是 ISO-8859-1，会把中文写坏（真实 HTTP 客户端无此问题）
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(created).path("data").asLong();
    }

    private List<String> titles(String token, String scope, String date) throws Exception {
        var req = get("/api/v1/tasks").param("scope", scope);
        if (date != null) {
            req = req.param("date", date);
        }
        String body = mvc.perform(req.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 同理：JSON 响应固定按 UTF-8 解码，不依赖响应头里的 charset
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode list = objectMapper.readTree(body).path("data").path("list");
        List<String> out = new ArrayList<>();
        list.forEach(n -> out.add(n.path("title").asText()));
        return out;
    }

    @Test
    void myDayIsAboutCreationDateNotDueDate() throws Exception {
        String token = login();

        // 1) 今天新建、**完全不设截止时间** —— 旧实现（按 due_at 开窗）永远看不到它
        create(token, "今天新建-无截止", null);
        // 2) 今天新建、截止时间在很远的将来 —— 旧实现同样会漏
        create(token, "今天新建-远期截止", "2030-10-01T10:00:00Z");

        List<String> day = titles(token, "day", null);
        assertTrue(day.contains("今天新建-无截止"), "不设截止时间的新任务必须出现在「我的一天」：" + day);
        assertTrue(day.contains("今天新建-远期截止"), "截止时间不在今天的新任务也应出现在「我的一天」：" + day);

        // 3) 截止时间就在今天、但**建立时间在昨天** —— 按旧错误口径会误入「我的一天」
        long stale = create(token, "昨天建立-今天截止", Instant.now().plus(2, ChronoUnit.HOURS).toString());
        Task backdate = new Task();
        backdate.setId(stale);
        backdate.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        taskMapper.updateById(backdate);

        List<String> dayAfter = titles(token, "day", null);
        assertFalse(dayAfter.contains("昨天建立-今天截止"),
                "「我的一天」只看建立时间，昨天建立的任务不应出现：" + dayAfter);

        // 4) 按日期参数取「那一天新建的任务」
        String yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        List<String> dayBefore = titles(token, "day", yesterday);
        assertTrue(dayBefore.contains("昨天建立-今天截止"), "指定日期应返回该日建立的任務：" + dayBefore);
        assertFalse(dayBefore.contains("今天新建-无截止"), "指定昨天不应包含今天建立的任务：" + dayBefore);

        // 5) 未来 7 天视图仍按截止时间开窗，且不受建立时间影响（回归保护）
        List<String> week = titles(token, "week", null);
        assertFalse(week.contains("今天新建-无截止"), "无截止时间的任务不应进入「未来7天」：" + week);
    }

    /**
     * 用户反馈：完成任务后它**从列表消失**了。
     *
     * <p>正确行为：已完成任务仍留在列表里（前端加删除线），并由 {@code TaskSorter}
     * 排在最后（逾期 &gt; 临期 &gt; 普通 &gt; 已完成）。旧系统同样是「未完成 + 已完成」都在，
     * 只是分两个查询（completeStatus=0/1）渲染成「未完成区 + 已完成折叠面板」。
     */
    @Test
    void completedTasksRemainVisibleAndSortLast() throws Exception {
        String token = login();
        create(token, "未完成-应在前", null);
        long done = create(token, "已完成-应在后", null);

        JsonNode detail = objectMapper.readTree(mvc.perform(get("/api/v1/tasks/" + done)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long version = detail.path("data").path("version").asLong();

        mvc.perform(post("/api/v1/tasks/" + done + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"version\":" + version + "}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.code").value(0));

        for (String scope : List.of("day", "all")) {
            List<String> list = titles(token, scope, null);
            assertTrue(list.contains("已完成-应在后"), scope + " 视图不应把已完成任务剔除：" + list);
            assertEquals("已完成-应在后", list.get(list.size() - 1), scope + " 视图应把已完成任务排在最后：" + list);
        }

        // 计数同样包含已完成（与旧系统 /task/record/count 口径一致）
        JsonNode counts = objectMapper.readTree(mvc.perform(get("/api/v1/tasks/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(2, counts.path("day").asInt(), "day 计数应含已完成：" + counts);
    }
}
