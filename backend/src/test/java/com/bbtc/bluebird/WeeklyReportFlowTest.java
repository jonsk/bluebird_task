package com.bbtc.bluebird;

import com.bbtc.bluebird.config.security.JwtUtils;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportMapper;
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

import java.nio.file.Files;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 周报接口：个人偏好、无模型时拒绝生成、跨用户隔离、乐观锁。 */
@SpringBootTest(properties = "app.config.release=false")
@AutoConfigureMockMvc
class WeeklyReportFlowTest {

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
    WeeklyReportMapper reportMapper;

    @Autowired
    JwtUtils jwtUtils;

    private SysUser user(String prefix) {
        SysUser u = new SysUser();
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        u.setUsername(name);
        u.setName(prefix);
        u.setRoleCode("COMMON");
        u.setStatus("ACTIVE");
        u.setDeleted(0);
        userMapper.insert(u);
        return u;
    }

    private String token(SysUser u) {
        return "Bearer " + jwtUtils.createAccess(u.getId(), u.getUsername(), u.getRoleCode());
    }

    @Test
    void preferenceDefaultsAndSaves() throws Exception {
        SysUser u = user("pref");

        mvc.perform(get("/api/v1/weekly-reports/preference").header("Authorization", token(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.style").value("concise"));

        mvc.perform(put("/api/v1/weekly-reports/preference")
                        .header("Authorization", token(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\":\"result\",\"focus\":\"delivery\",\"customRules\":\"short\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.style").value("result"))
                .andExpect(jsonPath("$.data.focus").value("delivery"));

        mvc.perform(get("/api/v1/weekly-reports/preference").header("Authorization", token(u)))
                .andExpect(jsonPath("$.data.customRules").value("short"));
    }

    @Test
    void generateWithoutModelIsRejected() throws Exception {
        SysUser u = user("nomodel");
        mvc.perform(post("/api/v1/weekly-reports/generate")
                        .header("Authorization", token(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void crossUserAccessIsNotFoundAndVersionConflictIsReported() throws Exception {
        SysUser owner = user("owner");
        SysUser other = user("other");

        WeeklyReport report = new WeeklyReport();
        report.setOwnerId(owner.getId());
        report.setWeekStart("2026-01-05");
        report.setWeekEnd("2026-01-11");
        report.setStatus("DRAFT");
        report.setContentHtml("<p>draft</p>");
        report.setVersion(0);
        report.setDeleted(0);
        report.setCreatedBy(owner.getId());
        reportMapper.insert(report);

        mvc.perform(get("/api/v1/weekly-reports/" + report.getId()).header("Authorization", token(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));

        mvc.perform(put("/api/v1/weekly-reports/" + report.getId())
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p>final</p>\",\"version\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));

        String body = mvc.perform(put("/api/v1/weekly-reports/" + report.getId())
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHtml\":\"<p>final</p>\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        org.junit.jupiter.api.Assertions.assertEquals("DONE", data.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("<p>final</p>", data.path("contentHtml").asText());
    }
}
