package com.bbtc.bluebird.modules.weeklyreport.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.RichTextGuard;
import com.bbtc.bluebird.modules.ai.application.AiModelService;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportPreference;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportBrief;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportGenerateReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportPreferenceReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportPreferenceVO;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportUpdateReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportVO;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportWeekVO;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportMapper;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportPreferenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人周报：列表 / 详情 / 手动生成 / 定稿 / 偏好 / 自动生成。
 *
 * <p>只读取当前用户自己的日志（{@code owner_id = me}），不做下属/部门口径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final WeeklyReportMapper reportMapper;
    private final WeeklyReportPreferenceMapper preferenceMapper;
    private final NoteMapper noteMapper;
    private final WeeklyReportGenerationService generationService;
    private final WeeklyReportContentRenderer renderer;
    private final AiModelService modelService;
    private final ObjectMapper objectMapper;

    /* ==================== 查询 ==================== */

    public PageResult<WeeklyReportBrief> page(long page, long size) {
        Long me = requireUser();
        long p = Math.max(1, page);
        long s = size < 1 ? 20 : Math.min(size, 100);
        IPage<WeeklyReport> result = reportMapper.selectPage(new Page<>(p, s),
                Wrappers.<WeeklyReport>lambdaQuery()
                        .eq(WeeklyReport::getOwnerId, me)
                        .orderByDesc(WeeklyReport::getWeekStart)
                        .orderByDesc(WeeklyReport::getId));
        List<WeeklyReportBrief> list = result.getRecords().stream().map(this::toBrief).toList();
        return PageResult.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public WeeklyReportVO detail(Long id) {
        return toVO(requireOwned(id));
    }

    /**
     * 可生成周报的周次列表（左侧导航）。
     *
     * <p>口径：只统计当前用户自己的日志，且
     * <ol>
     *   <li>周次不晚于当前周（未来的周没有意义）；</li>
     *   <li>该周至少有 1 条日志。</li>
     * </ol>
     * 该周已有周报时带上 {@code reportId} 与状态，没有则为 null，前端选中即可直接生成。
     * 结果按 weekStart 倒序（最近的周在最上面）。
     */
    public List<WeeklyReportWeekVO> weeks() {
        Long me = requireUser();
        String currentWeek = WeeklyReportPeriod.of(LocalDate.now(ZONE)).start();
        List<Note> notes = noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .eq(Note::getOwnerId, me)
                .eq(Note::getPeriodType, Note.PERIOD_DAY)
                .select(Note::getId, Note::getLogDate));
        Map<String, Long> counts = new HashMap<>();
        for (Note note : notes) {
            String start = weekStartOf(note.getLogDate());
            if (start == null || start.compareTo(currentWeek) > 0) {
                continue;
            }
            counts.merge(start, 1L, Long::sum);
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        Map<String, WeeklyReport> reports = new HashMap<>();
        for (WeeklyReport report : reportMapper.selectList(Wrappers.<WeeklyReport>lambdaQuery()
                .eq(WeeklyReport::getOwnerId, me))) {
            reports.put(report.getWeekStart(), report);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                .map(entry -> {
                    WeeklyReportPeriod period = WeeklyReportPeriod.of(LocalDate.parse(entry.getKey()));
                    WeeklyReport report = reports.get(entry.getKey());
                    return new WeeklyReportWeekVO(
                            period.start(), period.end(), entry.getValue(),
                            report == null ? null : report.getId(),
                            report == null ? null : report.getStatus(),
                            report == null ? null : report.getErrorMessage());
                })
                .toList();
    }

    /* ==================== 手动生成 ==================== */

    public WeeklyReportVO generate(WeeklyReportGenerateReq req) {
        Long me = requireUser();
        if (!modelService.hasActive()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "尚未启用 AI 模型，请联系管理员配置");
        }
        WeeklyReportPeriod period = periodOf(req == null ? null : req.weekStart());
        long noteCount = noteMapper.selectCount(Wrappers.<Note>lambdaQuery()
                .eq(Note::getOwnerId, me)
                .eq(Note::getPeriodType, Note.PERIOD_DAY)
                .ge(Note::getLogDate, period.start())
                .le(Note::getLogDate, period.end()));
        if (noteCount == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该周没有工作日志，无法生成 AI 周工作总结");
        }

        WeeklyReport report = findOwnedWeek(me, period.start());
        boolean overwrite = req != null && Boolean.TRUE.equals(req.overwrite());
        if (report != null) {
            if ("GENERATING".equals(report.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该周 AI 周工作总结正在生成，请稍候");
            }
            if (!overwrite && StringUtils.hasText(report.getContentHtml())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该周 AI 周工作总结已存在，如需重新生成请确认覆盖");
            }
            report.setStatus("GENERATING");
            report.setErrorMessage(null);
            reportMapper.updateById(report);
        } else {
            report = new WeeklyReport();
            report.setOwnerId(me);
            report.setWeekStart(period.start());
            report.setWeekEnd(period.end());
            report.setStatus("GENERATING");
            report.setVersion(0);
            report.setDeleted(0);
            report.setCreatedBy(me);
            try {
                reportMapper.insert(report);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该周 AI 周工作总结已存在，请刷新后重试");
            }
        }
        generationService.generate(report.getId(), me);
        return toVO(report);
    }

    /* ==================== 定稿 / 删除 ==================== */

    public WeeklyReportVO update(Long id, WeeklyReportUpdateReq req) {
        Long me = requireUser();
        WeeklyReport report = requireOwned(id);
        if ("GENERATING".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "正在生成中，暂不能编辑");
        }
        if (req.version() != null && !req.version().equals(report.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "AI 周工作总结已被修改，请刷新后重试");
        }
        // 正文来自前端编辑器，也可被直接调 API 伪造；落库前拒绝明确的 XSS 载荷。
        RichTextGuard.assertSafe(req.contentHtml());
        report.setContentHtml(req.contentHtml());
        report.setStatus("DONE");
        report.setUpdatedBy(me);
        reportMapper.updateById(report);
        return toVO(reportMapper.selectById(id));
    }

    public void delete(Long id) {
        requireUser();
        WeeklyReport report = requireOwned(id);
        reportMapper.deleteById(report.getId());
    }

    /* ==================== 个人偏好 ==================== */

    public WeeklyReportPreferenceVO preference() {
        Long me = requireUser();
        WeeklyReportPreference pref = findPreference(me);
        if (pref == null) {
            return new WeeklyReportPreferenceVO("concise", null, null);
        }
        return new WeeklyReportPreferenceVO(pref.getStyle(), pref.getFocus(), pref.getCustomRules());
    }

    @Transactional
    public WeeklyReportPreferenceVO savePreference(WeeklyReportPreferenceReq req) {
        Long me = requireUser();
        WeeklyReportPreference pref = findPreference(me);
        boolean isNew = pref == null;
        if (isNew) {
            pref = new WeeklyReportPreference();
            pref.setUserId(me);
            pref.setCreatedBy(me);
        }
        pref.setStyle(StringUtils.hasText(req.style()) ? req.style().trim() : "concise");
        pref.setFocus(trimToNull(req.focus()));
        pref.setCustomRules(trimToNull(req.customRules()));
        pref.setUpdatedBy(me);
        if (isNew) {
            preferenceMapper.insert(pref);
        } else {
            preferenceMapper.updateById(pref);
        }
        return new WeeklyReportPreferenceVO(pref.getStyle(), pref.getFocus(), pref.getCustomRules());
    }

    /* ==================== 自动生成（定时任务） ==================== */

    /**
     * 为本周有日志、且还没有周报的用户批量生成（生成成功即定稿）。
     *
     * <p>幂等：唯一索引 owner+week_start + 生成前先查，重复触发不会产生第二条。
     */
    public int autoGenerate() {
        if (!modelService.hasActive()) {
            log.warn("周报自动生成跳过：未启用 AI 模型");
            return 0;
        }
        WeeklyReportPeriod period = WeeklyReportPeriod.of(LocalDate.now(ZONE));
        List<Note> notes = noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .isNotNull(Note::getOwnerId)
                .eq(Note::getPeriodType, Note.PERIOD_DAY)
                .ge(Note::getLogDate, period.start())
                .le(Note::getLogDate, period.end())
                .select(Note::getId, Note::getOwnerId));
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Note note : notes) {
            counts.merge(note.getOwnerId(), 1, Integer::sum);
        }
        int created = 0;
        for (Long ownerId : counts.keySet()) {
            if (findOwnedWeek(ownerId, period.start()) != null) {
                continue;
            }
            WeeklyReport report = new WeeklyReport();
            report.setOwnerId(ownerId);
            report.setWeekStart(period.start());
            report.setWeekEnd(period.end());
            report.setStatus("GENERATING");
            report.setVersion(0);
            report.setDeleted(0);
            report.setCreatedBy(ownerId);
            try {
                reportMapper.insert(report);
            } catch (DuplicateKeyException e) {
                continue;
            }
            generationService.generate(report.getId(), ownerId);
            created += 1;
        }
        return created;
    }

    /* ==================== 内部 ==================== */

    private Long requireUser() {
        Long me = UserContext.currentUserId();
        if (me == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "未登录");
        }
        return me;
    }

    private WeeklyReport requireOwned(Long id) {
        Long me = requireUser();
        WeeklyReport report = reportMapper.selectById(id);
        if (report == null || !me.equals(report.getOwnerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 周工作总结不存在");
        }
        return report;
    }

    private WeeklyReport findOwnedWeek(Long ownerId, String weekStart) {
        return reportMapper.selectOne(Wrappers.<WeeklyReport>lambdaQuery()
                .eq(WeeklyReport::getOwnerId, ownerId)
                .eq(WeeklyReport::getWeekStart, weekStart)
                .last("limit 1"));
    }

    private String weekStartOf(String logDate) {
        if (!StringUtils.hasText(logDate)) {
            return null;
        }
        try {
            return WeeklyReportPeriod.of(LocalDate.parse(logDate.trim())).start();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private WeeklyReportPreference findPreference(Long userId) {
        return preferenceMapper.selectOne(Wrappers.<WeeklyReportPreference>lambdaQuery()
                .eq(WeeklyReportPreference::getUserId, userId)
                .last("limit 1"));
    }

    private WeeklyReportPeriod periodOf(String weekStart) {
        LocalDate date = LocalDate.now(ZONE);
        if (StringUtils.hasText(weekStart)) {
            try {
                date = LocalDate.parse(weekStart.trim());
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "日期格式非法（YYYY-MM-DD）");
            }
        }
        return WeeklyReportPeriod.of(date);
    }

    private WeeklyReportBrief toBrief(WeeklyReport r) {
        String summary = renderer.summaryOf(r.getAiResultJson());
        if (!StringUtils.hasText(summary)) {
            summary = null;
        }
        return new WeeklyReportBrief(r.getId(), r.getWeekStart(), r.getWeekEnd(), r.getStatus(),
                summary, r.getErrorMessage(), r.getGeneratedAt(), r.getUpdatedAt());
    }

    private WeeklyReportVO toVO(WeeklyReport r) {
        return new WeeklyReportVO(
                r.getId(), r.getOwnerId(), r.getWeekStart(), r.getWeekEnd(), r.getStatus(),
                r.getContentHtml(), r.getAiResultJson(), noteIds(r.getSourceNoteIds()),
                r.getModelName(), r.getErrorMessage(), r.getInputTokens(), r.getOutputTokens(),
                r.getGeneratedAt(), r.getCompletedAt(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getVersion());
    }

    private List<Long> noteIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Long.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /** 供测试与定时任务展示当前周期。 */
    public static String currentWeekStart() {
        return WeeklyReportPeriod.of(LocalDate.now(ZONE)).start();
    }
}
