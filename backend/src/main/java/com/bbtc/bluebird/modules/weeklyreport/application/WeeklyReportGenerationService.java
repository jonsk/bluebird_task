package com.bbtc.bluebird.modules.weeklyreport.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.ai.application.AiClient;
import com.bbtc.bluebird.modules.ai.application.AiModelService;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportPreference;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportMapper;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportPreferenceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周报异步生成器。
 *
 * <p>必须与 {@link WeeklyReportService} 分离：同类内部调用 {@code @Async} 不会走代理，
 * 生成会退化成同步请求，把 AI 超时直接压到 HTTP 线程上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportGenerationService {

    private static final int ERROR_LIMIT = 500;

    private final WeeklyReportMapper reportMapper;
    private final WeeklyReportPreferenceMapper preferenceMapper;
    private final NoteMapper noteMapper;
    private final SysUserMapper userMapper;
    private final AiModelService modelService;
    private final AiClient aiClient;
    private final WeeklyReportPromptBuilder promptBuilder;
    private final WeeklyReportContentRenderer renderer;
    private final ConfigService config;
    private final ObjectMapper objectMapper;

    @Async
    public void generate(Long reportId, Long userId) {
        WeeklyReport report = reportMapper.selectById(reportId);
        if (report == null || !userId.equals(report.getOwnerId())) {
            return;
        }
        Instant started = Instant.now();
        try {
            List<Note> notes = loadNotes(report, userId);
            if (notes.isEmpty()) {
                fail(report, "该周没有可用的工作日志");
                return;
            }
            SysUser user = userMapper.selectById(userId);
            WeeklyReportPreference pref = preferenceMapper.selectOne(
                    Wrappers.<WeeklyReportPreference>lambdaQuery()
                            .eq(WeeklyReportPreference::getUserId, userId)
                            .last("limit 1"));
            AiModelConfig model = modelService.activeEntity();
            WeeklyReportPromptBuilder.Prompt prompt = promptBuilder.build(
                    report.getWeekStart(), report.getWeekEnd(),
                    user == null ? null : user.getName(), notes, pref);

            AiClient.AiChatResult result = aiClient.chat(
                    model, modelService.decryptKey(model), prompt.system(), prompt.user(),
                    model.getMaxTokens() == null ? 2048 : model.getMaxTokens());

            JsonNode root = renderer.parse(result.content());
            WeeklyReportContentRenderer.Rendered rendered = root == null
                    ? renderer.renderRaw(result.content())
                    : renderer.render(root);
            if (rendered == null) {
                rendered = renderer.renderRaw(result.content());
            }

            // 生成即定稿：不再有「草稿 → 手动保存定稿」两步，用户后续修改走编辑器自动保存。
            report.setStatus("DONE");
            report.setContentHtml(rendered.html());
            report.setAiResultJson(root == null ? result.content() : root.toString());
            report.setSourceNoteIds(objectMapper.writeValueAsString(notes.stream().map(Note::getId).toList()));
            report.setSourceNoteVersions(objectMapper.writeValueAsString(versions(notes)));
            report.setModelConfigId(model.getId());
            report.setModelName(model.getName() + " / " + model.getModel());
            report.setPromptVersion(prompt.version());
            report.setUserPromptSnapshot(prompt.user());
            report.setInputTokens(result.inputTokens());
            report.setOutputTokens(result.outputTokens());
            report.setErrorMessage(null);
            report.setGeneratedAt(started);
            report.setCompletedAt(Instant.now());
            reportMapper.updateById(report);
            log.info("周报生成完成：report={} user={} notes={}", reportId, userId, notes.size());
        } catch (Exception e) {
            log.warn("周报生成失败：report={} user={} error={}", reportId, userId, e.getMessage());
            fail(report, e.getMessage());
        }
    }

    private List<Note> loadNotes(WeeklyReport report, Long userId) {
        int max = Math.max(1, Math.min(200, config.getInt("weeklyreport", "max-notes", 50)));
        List<Note> notes = noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .eq(Note::getOwnerId, userId)
                .ge(Note::getLogDate, report.getWeekStart())
                .le(Note::getLogDate, report.getWeekEnd())
                .orderByDesc(Note::getLogDate)
                .orderByDesc(Note::getCreatedAt)
                .last("limit " + max));
        // 提示词里按时间正序更符合叙事，查询倒序只是为了取「最近 N 条」。
        return notes.reversed();
    }

    private Map<String, Object> versions(List<Note> notes) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Note note : notes) {
            out.put(String.valueOf(note.getId()), note.getVersion());
        }
        return out;
    }

    private void fail(WeeklyReport report, String message) {
        try {
            report.setStatus("FAILED");
            report.setErrorMessage(abbreviate(message));
            report.setCompletedAt(Instant.now());
            reportMapper.updateById(report);
        } catch (Exception e) {
            log.warn("周报失败状态写入失败：{}", e.getMessage());
        }
    }

    private String abbreviate(String message) {
        String text = message == null || message.isBlank() ? "生成失败" : message.replaceAll("\\s+", " ").trim();
        return text.length() <= ERROR_LIMIT ? text : text.substring(0, ERROR_LIMIT);
    }
}
