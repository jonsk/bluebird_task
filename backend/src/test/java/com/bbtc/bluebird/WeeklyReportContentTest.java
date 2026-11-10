package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportContentRenderer;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 周报周期与 AI 输出渲染的纯逻辑测试（不启动 Spring）。 */
class WeeklyReportContentTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WeeklyReportContentRenderer renderer = new WeeklyReportContentRenderer(mapper);

    @Test
    void periodNormalizesToMondayThroughSunday() {
        WeeklyReportPeriod period = WeeklyReportPeriod.of(LocalDate.of(2026, 11, 10)); // 周二
        assertEquals("2026-11-09", period.start());
        assertEquals("2026-11-15", period.end());

        WeeklyReportPeriod sunday = WeeklyReportPeriod.of(LocalDate.of(2026, 11, 15));
        assertEquals("2026-11-09", sunday.start());
        assertEquals("2026-11-15", sunday.end());
    }

    @Test
    void parsesJsonWrappedInFences() {
        JsonNode root = renderer.parse("```json\n{\"summary\":\"完成 A\",\"achievements\":[]}\n```");
        assertEquals("完成 A", root.path("summary").asText());
    }

    @Test
    void rendersSectionsAndCitations() {
        JsonNode root = renderer.parse("""
                {"summary":"本周完成登录改造",
                 "achievements":[{"text":"完成 JWT 续签","noteIds":[11,12]}],
                 "risks":["测试环境不稳定"],
                 "metrics":{"提交次数":"8"}}
                """);
        WeeklyReportContentRenderer.Rendered rendered = renderer.render(root);
        assertTrue(rendered.html().contains("<h2>本周概述</h2>"));
        assertTrue(rendered.html().contains(
                "完成 JWT 续签（<a href=\"/notes/11\">#11</a>、<a href=\"/notes/12\">#12</a>）"));
        assertTrue(rendered.html().contains("测试环境不稳定"));
        assertTrue(rendered.html().contains("提交次数：8"));
        assertEquals("本周完成登录改造", rendered.summary());
    }

    @Test
    void citationLinksDropDuplicatedMarkerAndInvalidIds() {
        JsonNode root = renderer.parse("""
                {"achievements":[{"text":"完成联调（#369165902139392）","noteIds":[369165902139392,"abc"]},
                                 {"text":"无引用"}]}
                """);
        String html = renderer.render(root).html();
        // 正文里的手写引用被剥掉，只保留统一渲染的链接，不会出现「##369」
        assertTrue(html.contains("完成联调（<a href=\"/notes/369165902139392\">#369165902139392</a>）"), html);
        assertFalse(html.contains("##"), html);
        assertFalse(html.contains("abc"), html);
    }

    @Test
    void escapesModelOutput() {
        JsonNode root = renderer.parse("""
                {"summary":"<script>alert(1)</script>","achievements":[]}
                """);
        String html = renderer.render(root).html();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void invalidJsonFallsBackToRawText() {
        assertNull(renderer.parse("这不是 JSON"));
        WeeklyReportContentRenderer.Rendered rendered = renderer.renderRaw("1. 完成 A\n2. 推进 B");
        assertTrue(rendered.html().contains("完成 A"));
        assertTrue(rendered.html().contains("<br>"));
    }
}
