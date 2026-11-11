package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.ai.application.AiClient;
import com.bbtc.bluebird.modules.ai.application.AiModelService;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportContentRenderer;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportGenerationService;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportPromptBuilder;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportMapper;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportPreferenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步生成周报的编排与失败记账。
 *
 * <p>重点覆盖三条此前无断言的路径：空日志直接失败、AI 异常落 FAILED、AI 输出的危险 HTML 被
 * RichTextGuard 拦下（不得把 script 写进已定稿内容）。
 */
class WeeklyReportGenerationServiceTest {

    private static final long OWNER = 1L;
    private static final long REPORT_ID = 100L;

    private WeeklyReportMapper reportMapper;
    private WeeklyReportPreferenceMapper preferenceMapper;
    private NoteMapper noteMapper;
    private SysUserMapper userMapper;
    private AiModelService modelService;
    private AiClient aiClient;
    private WeeklyReportPromptBuilder promptBuilder;
    private WeeklyReportContentRenderer renderer;
    private ConfigService config;
    private WeeklyReportGenerationService service;

    private WeeklyReport report;
    private AiModelConfig model;

    @BeforeEach
    void setUp() {
        reportMapper = mock(WeeklyReportMapper.class);
        preferenceMapper = mock(WeeklyReportPreferenceMapper.class);
        noteMapper = mock(NoteMapper.class);
        userMapper = mock(SysUserMapper.class);
        modelService = mock(AiModelService.class);
        aiClient = mock(AiClient.class);
        promptBuilder = mock(WeeklyReportPromptBuilder.class);
        renderer = mock(WeeklyReportContentRenderer.class);
        config = mock(ConfigService.class);

        // 真实的 ObjectMapper：序列化 sourceNoteIds / versions 是这条链路的一部分
        service = new WeeklyReportGenerationService(reportMapper, preferenceMapper, noteMapper, userMapper,
                modelService, aiClient, promptBuilder, renderer, config, new ObjectMapper());

        report = new WeeklyReport();
        report.setId(REPORT_ID);
        report.setOwnerId(OWNER);
        report.setStatus("RUNNING");
        report.setWeekStart("2026-11-02");
        report.setWeekEnd("2026-11-08");

        model = new AiModelConfig();
        model.setId(9L);
        model.setName("内网模型");
        model.setModel("qwen-max");
        model.setMaxTokens(1024);

        when(reportMapper.selectById(REPORT_ID)).thenReturn(report);
        when(config.getInt(eq("weeklyreport"), eq("max-notes"), anyInt())).thenReturn(50);
    }

    @Test
    void 报告不存在时直接返回不写库() {
        when(reportMapper.selectById(anyLong())).thenReturn(null);

        service.generate(REPORT_ID, OWNER);

        verify(reportMapper, never()).updateById(any(WeeklyReport.class));
    }

    @Test
    void 报告不属于当前用户时直接返回() {
        service.generate(REPORT_ID, 999L);

        verify(reportMapper, never()).updateById(any(WeeklyReport.class));
        verify(aiClient, never()).chat(any(), any(), any(), any(), anyInt());
    }

    @Test
    void 该周没有日志时标为失败并给出原因() {
        when(noteMapper.selectList(any())).thenReturn(List.of());

        service.generate(REPORT_ID, OWNER);

        assertEquals("FAILED", report.getStatus());
        assertEquals("该周没有可用的工作日志", report.getErrorMessage());
        assertNull(report.getContentHtml());
        verify(reportMapper).updateById(report);
        verify(aiClient, never()).chat(any(), any(), any(), any(), anyInt());
    }

    @Test
    void AI调用异常时记录失败且错误信息被压平截断() {
        when(noteMapper.selectList(any())).thenReturn(List.of(note(1L)));
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "usr", "v1"));
        when(aiClient.chat(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("连接被重置\n\t超时"));

        service.generate(REPORT_ID, OWNER);

        assertEquals("FAILED", report.getStatus());
        assertEquals("连接被重置 超时", report.getErrorMessage(), "换行/制表符要压成单个空格");
    }

    @Test
    void 错误信息超长时截断到500字() {
        when(noteMapper.selectList(any())).thenReturn(List.of(note(1L)));
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "usr", "v1"));
        when(aiClient.chat(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("错".repeat(1200)));

        service.generate(REPORT_ID, OWNER);

        assertEquals("FAILED", report.getStatus());
        assertEquals(500, report.getErrorMessage().length());
    }

    @Test
    void 生成成功时写入定稿内容与元数据() {
        List<Note> notes = List.of(note(11L), note(12L));
        when(noteMapper.selectList(any())).thenReturn(notes);
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), eq("张三"), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "user prompt body", "v2"));
        when(aiClient.chat(any(), any(), any(), any(), eq(1024)))
                .thenReturn(new AiClient.AiChatResult("{\"summary\":\"ok\"}", 120, 30));
        var root = new ObjectMapper().createObjectNode().put("summary", "ok");
        when(renderer.parse("{\"summary\":\"ok\"}")).thenReturn(root);
        when(renderer.render(root)).thenReturn(new WeeklyReportContentRenderer.Rendered("<p>本周进展</p>", "本周进展"));

        service.generate(REPORT_ID, OWNER);

        assertEquals("DONE", report.getStatus());
        assertEquals("<p>本周进展</p>", report.getContentHtml());
        // 查询按 logDate/createdAt 倒序取「最近 N 条」，落库的 sourceNoteIds 会按时间正序反转
        assertEquals("[12,11]", report.getSourceNoteIds());
        assertEquals("内网模型 / qwen-max", report.getModelName());
        assertEquals("v2", report.getPromptVersion());
        assertEquals("user prompt body", report.getUserPromptSnapshot());
        assertEquals(120, report.getInputTokens());
        assertEquals(30, report.getOutputTokens());
        assertNull(report.getErrorMessage());
        assertEquals(9L, report.getModelConfigId());
        assertTrue(report.getCompletedAt() != null);
        verify(reportMapper).updateById(report);
    }

    @Test
    void AI输出含危险HTML时拒绝落库并标为失败() {
        when(noteMapper.selectList(any())).thenReturn(List.of(note(1L)));
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "usr", "v1"));
        when(aiClient.chat(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AiClient.AiChatResult("raw", 1, 1));
        when(renderer.parse("raw")).thenReturn(null);
        when(renderer.renderRaw("raw"))
                .thenReturn(new WeeklyReportContentRenderer.Rendered("<script>alert(1)</script>", "x"));

        service.generate(REPORT_ID, OWNER);

        assertEquals("FAILED", report.getStatus(), "危险载荷必须拦在落库前");
        assertNull(report.getContentHtml());
        assertTrue(report.getErrorMessage() != null && !report.getErrorMessage().isBlank());
    }

    @Test
    void 模型未配maxTokens时用2048兜底() {
        model.setMaxTokens(null);
        when(noteMapper.selectList(any())).thenReturn(List.of(note(1L)));
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "usr", "v1"));
        when(aiClient.chat(any(), any(), any(), any(), eq(2048)))
                .thenReturn(new AiClient.AiChatResult("raw", 1, 1));
        when(renderer.parse("raw")).thenReturn(null);
        when(renderer.renderRaw("raw")).thenReturn(new WeeklyReportContentRenderer.Rendered("<p>ok</p>", "ok"));

        service.generate(REPORT_ID, OWNER);

        verify(aiClient).chat(any(), anyString(), anyString(), anyString(), eq(2048));
    }

    @Test
    void 渲染器返回null时回落到原始文本() {
        when(noteMapper.selectList(any())).thenReturn(List.of(note(1L)));
        when(userMapper.selectById(OWNER)).thenReturn(user());
        when(modelService.activeEntity()).thenReturn(model);
        when(modelService.decryptKey(model)).thenReturn("k");
        when(promptBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new WeeklyReportPromptBuilder.Prompt("sys", "usr", "v1"));
        when(aiClient.chat(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AiClient.AiChatResult("raw", 1, 1));
        var root = new ObjectMapper().createObjectNode();
        when(renderer.parse("raw")).thenReturn(root);
        when(renderer.render(root)).thenReturn(null);
        when(renderer.renderRaw("raw")).thenReturn(new WeeklyReportContentRenderer.Rendered("<p>裸文本</p>", "裸文本"));

        service.generate(REPORT_ID, OWNER);

        assertEquals("DONE", report.getStatus());
        assertEquals("<p>裸文本</p>", report.getContentHtml());
    }

    @Test
    void 失败状态写库异常时不影响流程() {
        when(noteMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.updateById(any(WeeklyReport.class))).thenThrow(new IllegalStateException("库挂了"));

        service.generate(REPORT_ID, OWNER);

        // 不应抛出：审计/状态写入失败不能把异步任务打崩
        verify(reportMapper).updateById(any(WeeklyReport.class));
    }

    private Note note(Long id) {
        Note note = new Note();
        note.setId(id);
        note.setOwnerId(OWNER);
        note.setLogDate("2026-11-03");
        note.setContent("今天修了登录");
        note.setVersion(3);
        return note;
    }

    private SysUser user() {
        SysUser u = new SysUser();
        u.setId(OWNER);
        u.setName("张三");
        return u;
    }
}
