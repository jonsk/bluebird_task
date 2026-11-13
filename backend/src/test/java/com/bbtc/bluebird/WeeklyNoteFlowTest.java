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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 每周工作记录：一周一条（幂等 + 唯一索引）、默认 7 个日期标题、
 * 与日日志在列表/日历/摘要上的隔离、上级只读、删除后重建与回收站恢复冲突。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.security.manager-can-read-subordinate=true"
})
@AutoConfigureMockMvc
class WeeklyNoteFlowTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate MONDAY =
            LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-weekly-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        try {
            String dir = Files.createTempDirectory("bb-weekly-files").toString();
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

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, SysUser u) {
        return b.header("Authorization", "Bearer " + jwtUtils.createAccess(u.getId(), u.getUsername(), u.getRoleCode()));
    }

    private JsonNode createWeekly(SysUser u) throws Exception {
        String body = mvc.perform(auth(post("/api/v1/notes/weekly"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode createWeeklyAt(SysUser u, String weekStart) throws Exception {
        String body = mvc.perform(auth(post("/api/v1/notes/weekly"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"" + weekStart + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /* ==================== 用例 ==================== */

    @Test
    void createsOneRecordPerWeekWithSevenDateHeadings() throws Exception {
        SysUser u = user("weekly", null, "COMMON");

        JsonNode first = createWeekly(u);
        long id = first.path("id").asLong();
        String content = first.path("content").asText();

        // 7 个一级标题，日期从本周周一开始，逐日取自己的年月（跨月/跨年不会写错）
        assertEquals(7, content.split("<h1>", -1).length - 1);
        assertTrue(content.startsWith("<h1>" + heading(MONDAY, "周一") + "</h1>"), content);
        assertTrue(content.contains("</h1><h1>" + heading(MONDAY.plusDays(6), "周日") + "</h1>"), content);
        assertEquals(MONDAY.toString(), first.path("logDate").asText());
        // 只有标题没有正文 → 摘要为空（前端展示「本周暂无内容」）
        assertEquals("", first.path("contentSummary").asText());

        // 幂等：再次调用返回同一条，不新建、不覆盖
        JsonNode second = createWeekly(u);
        assertEquals(id, second.path("id").asLong());
        assertEquals(content, second.path("content").asText());
    }

    @Test
    void createsRequestedFutureWeekAndNormalizesToMonday() throws Exception {
        SysUser u = user("future", null, "COMMON");
        LocalDate futureMonday = MONDAY.plusWeeks(3);

        JsonNode created = createWeeklyAt(u, futureMonday.plusDays(4).toString());
        long id = created.path("id").asLong();
        String content = created.path("content").asText();

        assertEquals(futureMonday.toString(), created.path("logDate").asText());
        assertEquals(7, content.split("<h1>", -1).length - 1);
        assertTrue(content.startsWith("<h1>" + heading(futureMonday, "周一") + "</h1>"), content);
        assertTrue(content.contains("</h1><h1>" + heading(futureMonday.plusDays(6), "周日") + "</h1>"), content);

        // 幂等：同一周再次创建返回同一条，不覆盖
        JsonNode again = createWeeklyAt(u, futureMonday.toString());
        assertEquals(id, again.path("id").asLong());
        assertEquals(content, again.path("content").asText());
    }

    @Test
    void rejectsCurrentAndPastWeekWhenExplicitlyRequested() throws Exception {
        SysUser u = user("past", null, "COMMON");

        for (String week : new String[]{MONDAY.toString(), MONDAY.minusWeeks(1).toString()}) {
            mvc.perform(auth(post("/api/v1/notes/weekly"), u)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"weekStart\":\"" + week + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void weeklyAndDailyRecordsAreIsolated() throws Exception {
        SysUser u = user("isolation", null, "COMMON");
        long weeklyId = createWeekly(u).path("id").asLong();
        long dailyId = createDaily(u);

        // 日列表只看到日日志
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(dailyId));

        // 周列表只看到周记录
        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("period", "WEEK"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(weeklyId))
                .andExpect(jsonPath("$.data.list[0].logDate").value(MONDAY.toString()));

        // 日历不统计周记录
        mvc.perform(auth(get("/api/v1/notes/calendar")
                        .param("year", String.valueOf(LocalDate.now(ZONE).getYear()))
                        .param("month", String.valueOf(LocalDate.now(ZONE).getMonthValue()))
                        .param("scope", "mine"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].date").value(
                        LocalDate.now(ZONE).toString()));
    }

    @Test
    void weeklySummarySkipsDateHeadings() throws Exception {
        SysUser u = user("summary", null, "COMMON");
        long id = createWeekly(u).path("id").asLong();
        String body = "<h1>" + heading(MONDAY, "周一") + "</h1><p>完成接口联调</p>";
        for (int i = 1; i < 7; i++) {
            body += "<h1>" + heading(MONDAY.plusDays(i), weekdayCn(i)) + "</h1><p></p>";
        }
        mvc.perform(auth(put("/api/v1/notes/" + id), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + body.replace("\"", "\\\"") + "\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(auth(get("/api/v1/notes").param("scope", "mine").param("period", "WEEK"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].contentSummary").value("完成接口联调"));
    }

    @Test
    void managerCanReadSubordinateWeeklyButNotEdit() throws Exception {
        SysUser manager = user("manager", null, "COMMON");
        Department root = dept("研发部", null, manager.getId());
        Department child = dept("研发一组", root.getId(), null);
        manager.setDeptId(root.getId());
        userMapper.updateById(manager);
        SysUser sub = user("sub", child.getId(), "COMMON");

        long weeklyId = createWeekly(sub).path("id").asLong();

        mvc.perform(auth(get("/api/v1/notes/" + weeklyId), manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEdit").value(false));

        mvc.perform(auth(put("/api/v1/notes/" + weeklyId), manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"<p>改一下</p>\",\"version\":0}"))
                .andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/v1/notes").param("scope", "subordinate").param("period", "WEEK"), manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(weeklyId));
    }

    @Test
    void deletingWeeklyAllowsRecreateButRestoreOfOldOneIsRejected() throws Exception {
        SysUser u = user("recycle", null, "COMMON");
        long firstId = createWeekly(u).path("id").asLong();

        mvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/notes/" + firstId), u))
                .andExpect(status().isOk());

        JsonNode recreated = createWeekly(u);
        long secondId = recreated.path("id").asLong();
        assertTrue(secondId != firstId);

        // 同一周已有未删除记录 → 恢复旧记录给出可操作提示，而不是 500
        mvc.perform(auth(post("/api/v1/recycle/notes/" + firstId + "/restore"), u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value(containsString("该周已有工作记录")));
    }

    private long createDaily(SysUser u) throws Exception {
        String body = mvc.perform(auth(post("/api/v1/notes"), u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"<p>今天的日志</p>\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private static String heading(LocalDate d, String weekday) {
        return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日 " + weekday;
    }

    private static String weekdayCn(int index) {
        return new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"}[index];
    }
}
