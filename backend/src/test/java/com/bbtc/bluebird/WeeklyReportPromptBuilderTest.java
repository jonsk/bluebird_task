package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.config.application.ConfigDefinition;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportPromptBuilder;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportPreference;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 提示词组装：变量替换、管理员改模板时的兜底、偏好规则、日志裁剪、生成类型。
 */
class WeeklyReportPromptBuilderTest {

    private static final String TEMPLATE = """
            请为 {{userName}} 生成本周总结：{{weekStart}} ~ {{weekEnd}}
            类型：{{reportType}}
            规则：{{userRules}}
            日志：{{notes}}
            """;

    private ConfigService config;
    private WeeklyReportPromptBuilder builder;

    @BeforeEach
    void setUp() {
        config = mock(ConfigService.class);
        builder = new WeeklyReportPromptBuilder(config);
    }

    @Test
    void 模板变量被替换() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(1L, "2026-11-03", "完成了登录改造")), null, WeeklyReportType.DAILY);

        assertTrue(prompt.user().contains("张三"));
        assertTrue(prompt.user().contains("2026-11-02 ~ 2026-11-08"));
        assertTrue(prompt.user().contains("类型：每日工作记录"));
        assertTrue(prompt.user().contains("[#1] 2026-11-03"));
        assertTrue(prompt.user().contains("完成了登录改造"));
        assertTrue(prompt.user().contains("无补充规则"));
        assertTrue(prompt.system().contains("JSON"), "系统提示词要保留 JSON 输出约束");
        assertTrue(prompt.system().contains("每日工作记录"), "系统提示词要限定本次生成类型");
    }

    @Test
    void 模板缺少日志变量时末尾兜底补上() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString()))
                .thenReturn("只为 {{userName}} 生成总结，别忘规则：{{userRules}}");

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(7L, "2026-11-04", "修复了导出乱码")), null, WeeklyReportType.DAILY);

        assertTrue(prompt.user().contains("工作记录"), "模板丢了 {{notes}} 必须仍把记录补进去");
        assertTrue(prompt.user().contains("[#7]"));
        assertTrue(prompt.user().contains("修复了导出乱码"));
    }

    @Test
    void 模板缺少规则变量时末尾兜底补上用户偏好() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString()))
                .thenReturn("总结：{{weekStart}} ~ {{weekEnd}} {{notes}}");
        WeeklyReportPreference pref = preference("result", "交付风险", "不要写客套话");

        var prompt = builder.build("2026-11-02", "2026-11-08", null,
                List.of(note(1L, "2026-11-03", "内容")), pref, WeeklyReportType.DAILY);

        assertTrue(prompt.user().contains("用户补充规则"));
        assertTrue(prompt.user().contains("结果导向"));
        assertTrue(prompt.user().contains("交付风险"));
        assertTrue(prompt.user().contains("不要写客套话"));
    }

    @Test
    void 空模板回落默认模板() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn("  ");

        var prompt = builder.build("2026-11-02", "2026-11-08", "李四",
                List.of(note(1L, "2026-11-03", "内容")), null, WeeklyReportType.WEEKLY);

        assertTrue(prompt.user().contains("李四"));
        assertTrue(prompt.user().contains("[#1]"), "默认模板也要带记录块");
        assertTrue(prompt.user().contains("每周工作记录"));
        assertFalse(prompt.user().contains("{{"), "默认模板的占位符必须全部替换");
    }

    @Test
    void 单篇日志超长被裁剪且保留标识() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);

        String longText = "进展".repeat(2000);
        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(3L, "2026-11-05", longText)), null, WeeklyReportType.DAILY);

        assertTrue(prompt.user().contains("[#3]"));
        assertFalse(prompt.user().contains(longText), "超长正文必须被裁剪，避免撑爆上下文");
    }

    @Test
    void 日志总量超限时后续条目不进提示词() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);

        String big = "内容".repeat(3000);
        List<Note> notes = List.of(
                note(1L, "2026-11-03", big),
                note(2L, "2026-11-04", big),
                note(3L, "2026-11-05", big),
                note(4L, "2026-11-06", big));

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三", notes, null, WeeklyReportType.DAILY);

        assertTrue(prompt.user().contains("[#1]"));
        assertTrue(prompt.user().length() < 30000, "总长要受 TOTAL_CHAR_LIMIT 约束");
    }

    @Test
    void 空白日志内容不产生空条目() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(9L, "2026-11-03", "   ")), null, WeeklyReportType.DAILY);

        assertFalse(prompt.user().contains("[#9]"), "纯空白日志应被跳过");
    }

    @Test
    void 每日与每周记录各自标注来源() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);
        Note daily = note(1L, "2026-11-03", "完成了登录改造");
        Note weekly = note(21L, "2026-11-02", "本周整体完成登录与权限改造");
        weekly.setPeriodType(Note.PERIOD_WEEK);
        weekly.setPeriodStart("2026-11-02");

        var dailyPrompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(daily), null, WeeklyReportType.DAILY);
        var weeklyPrompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(weekly), null, WeeklyReportType.WEEKLY);

        assertTrue(dailyPrompt.user().contains("[#1] 2026-11-03（每日工作记录）"));
        assertTrue(weeklyPrompt.user().contains("[#21] 2026-11-02（每周工作记录）"));
        assertTrue(dailyPrompt.system().contains("每日工作记录"));
        assertTrue(weeklyPrompt.system().contains("每周工作记录"));
    }

    @Test
    void 旧模板缺少输出结构时末尾兜底补上() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString())).thenReturn(TEMPLATE);

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(1L, "2026-11-03", "内容")), null, WeeklyReportType.WEEKLY);

        assertTrue(prompt.user().contains("\"achievements\""), "旧模板没有 {{outputSchema}} 时必须补固定结构");
        assertTrue(prompt.user().contains("本次只总结「每周工作记录」"));
        assertFalse(prompt.user().contains("dailyWork"), "新结构不再按来源分组");
        assertFalse(prompt.user().contains("weeklyWork"));
    }

    @Test
    void 默认模板注入类型与输出结构() {
        when(config.get(eq("weeklyreport"), eq("prompt-template"), anyString()))
                .thenReturn(ConfigDefinition.DEFAULT_WEEKLY_PROMPT);

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三",
                List.of(note(1L, "2026-11-03", "内容")), null, WeeklyReportType.WEEKLY);

        assertTrue(prompt.user().contains("本次请只根据下面提供的「每周工作记录」"));
        assertTrue(prompt.user().contains("\"achievements\""));
        assertFalse(prompt.user().contains("{{outputSchema}}"));
        assertFalse(prompt.user().contains("{{reportType}}"));
    }

    @Test
    void 提示词版本号固定输出() {
        when(config.get(any(), any(), any())).thenReturn(TEMPLATE);

        var prompt = builder.build("2026-11-02", "2026-11-08", "张三", List.of(), null,
                WeeklyReportType.WEEKLY);

        assertTrue(prompt.version() != null && !prompt.version().isBlank(), "版本号要落库，便于回溯");
    }

    private Note note(Long id, String logDate, String content) {
        Note note = new Note();
        note.setId(id);
        note.setLogDate(logDate);
        note.setContent(content);
        note.setVersion(1);
        return note;
    }

    private WeeklyReportPreference preference(String style, String focus, String customRules) {
        WeeklyReportPreference pref = new WeeklyReportPreference();
        pref.setStyle(style);
        pref.setFocus(focus);
        pref.setCustomRules(customRules);
        return pref;
    }
}
