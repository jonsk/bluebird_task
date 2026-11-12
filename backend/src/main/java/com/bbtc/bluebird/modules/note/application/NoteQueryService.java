package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.ContentTypes;
import com.bbtc.bluebird.common.util.MarkdownText;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.dto.NoteAttachmentVO;
import com.bbtc.bluebird.modules.note.dto.NoteBrief;
import com.bbtc.bluebird.modules.note.dto.NoteCalendarDay;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteQuery;
import com.bbtc.bluebird.modules.note.dto.NoteVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.util.NoteVisibilitySqlBuilder;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日志读查询：4 种 scope 列表、详情、日历。
 *
 * <p>所有 scope 均通过 {@link NoteVisibilitySqlBuilder} 生成条件（与 isVisible 同源）。
 */
@Service
@RequiredArgsConstructor
public class NoteQueryService {

    private static final int SUMMARY_LIMIT = 200;
    /** 周记录的默认正文是 7 个日期一级标题，摘要要把它们整段剥掉，否则列表上全是「2026年11月2日 周一…」。 */
    private static final Pattern H1_BLOCK = Pattern.compile("(?is)<h1\\b[^>]*>.*?</h1>");

    private final NoteMapper noteMapper;
    private final NoteAttachmentRefMapper attachmentRefMapper;
    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AttachmentMapper attachmentMapper;
    private final NoteAuthService authService;
    private final NoteVisibilitySqlBuilder visibilitySql;

    /* ==================== 列表 ==================== */

    public PageResult<NoteBrief> page(NoteQuery q) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        String scope = StringUtils.hasText(q.scope()) ? q.scope() : "mine";
        if (!visibilitySql.validScope(scope)) {
            throw HttpStatusException.badRequest("非法 scope");
        }
        long page = Math.max(1, q.page());
        long size = q.size() < 1 ? 20 : Math.min(q.size(), 100);

        String period = periodOf(q.period());
        LambdaQueryWrapper<Note> w = Wrappers.lambdaQuery();
        w.eq(Note::getPeriodType, period);
        w.apply(visibilitySql.scopePredicate(scope, me, admin));
        applyKeyword(w, q.keyword());
        applyDate(w, q, period);
        applySort(w, q.sort(), period);

        IPage<Note> p = noteMapper.selectPage(new Page<>(page, size), w);
        List<NoteBrief> list = toBriefs(p.getRecords());
        return PageResult.of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 周期参数归一：缺省/空 → DAY；只接受 DAY|WEEK。 */
    static String periodOf(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Note.PERIOD_DAY;
        }
        String p = raw.trim().toUpperCase(Locale.ROOT);
        if (Note.PERIOD_DAY.equals(p) || Note.PERIOD_WEEK.equals(p)) {
            return p;
        }
        throw HttpStatusException.badRequest("非法 period，只支持 DAY 或 WEEK");
    }

    private void applyKeyword(LambdaQueryWrapper<Note> w, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        w.apply("note.content LIKE {0} ESCAPE '\\'", likePattern(keyword.trim()));
    }

    private void applyDate(LambdaQueryWrapper<Note> w, NoteQuery q, String period) {
        SFunction<Note, String> column = Note.PERIOD_WEEK.equals(period)
                ? Note::getPeriodStart : Note::getLogDate;
        if (q.date() != null) {
            if (q.start() != null || q.end() != null) {
                throw HttpStatusException.badRequest("date 与 start/end 互斥");
            }
            w.eq(column, q.date().toString());
            return;
        }
        if (q.start() != null) {
            w.ge(column, q.start().toString());
        }
        if (q.end() != null) {
            w.le(column, q.end().toString());
        }
    }

    private void applySort(LambdaQueryWrapper<Note> w, String sort, String period) {
        SFunction<Note, String> column = Note.PERIOD_WEEK.equals(period)
                ? Note::getPeriodStart : Note::getLogDate;
        String s = StringUtils.hasText(sort) ? sort : "log_date_desc";
        switch (s) {
            case "log_date_asc" -> w.orderByAsc(column).orderByAsc(Note::getCreatedAt);
            case "created_desc" -> w.orderByDesc(Note::getCreatedAt);
            case "updated_desc" -> w.orderByDesc(Note::getUpdatedAt).orderByDesc(Note::getCreatedAt);
            default -> w.orderByDesc(column).orderByDesc(Note::getCreatedAt);
        }
    }

    private String likePattern(String kw) {
        String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /* ==================== 详情 ==================== */

    public NoteDetailVO detail(Long id) {
        Note note = authService.requireVisible(id);
        return toDetail(note);
    }

    public NoteDetailVO toDetail(Note note) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        NoteDetailVO vo = new NoteDetailVO();
        fillBase(vo, note, me);
        vo.setContent(note.getContent());
        vo.setFieldPermissions(authService.fieldPermissions(note, me));
        vo.setAttachments(attachmentVOs(note, me, admin));
        return vo;
    }

    /* ==================== 日历 ==================== */

    public List<NoteCalendarDay> calendar(int year, int month, String scope) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        String s = StringUtils.hasText(scope) ? scope : "mine";
        if (!visibilitySql.validScope(s)) {
            throw HttpStatusException.badRequest("非法 scope");
        }
        YearMonth ym = YearMonth.of(year, month);
        QueryWrapper<Note> w = new QueryWrapper<>();
        w.select("note.log_date AS d", "COUNT(*) AS c");
        w.apply(visibilitySql.scopePredicate(s, me, admin));
        w.apply("note.deleted = 0");
        // 日志日历只统计日粒度；周记录按周展示，不进入日视图
        w.apply("note.period_type = {0}", Note.PERIOD_DAY);
        w.between("note.log_date", ym.atDay(1).toString(), ym.atEndOfMonth().toString());
        w.groupBy("note.log_date");
        w.orderByAsc("note.log_date");
        List<Map<String, Object>> rows = noteMapper.selectMaps(w);
        List<NoteCalendarDay> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object d = valueIgnoreCase(row, "d", "log_date");
            Object c = valueIgnoreCase(row, "c", "count");
            if (d != null) {
                out.add(new NoteCalendarDay(String.valueOf(d), c == null ? 0 : ((Number) c).longValue()));
            }
        }
        return out;
    }

    private Object valueIgnoreCase(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) {
                return row.get(k);
            }
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey().equalsIgnoreCase(k)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    /* ==================== VO 组装 ==================== */

    private List<NoteBrief> toBriefs(List<Note> notes) {
        if (notes.isEmpty()) {
            return List.of();
        }
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        Set<Long> ownerIds = new HashSet<>();
        notes.forEach(n -> ownerIds.add(n.getOwnerId()));
        Map<Long, String> ownerNames = userNames(ownerIds);

        List<NoteBrief> out = new ArrayList<>();
        for (Note n : notes) {
            out.add(new NoteBrief(n.getId(), summaryOf(n), n.getLogDate(),
                    n.getOwnerId(),
                    ownerNames.get(n.getOwnerId()),
                    canEdit(n, me, admin), canManage(n, me, admin), versionOf(n)));
        }
        return out;
    }

    private void fillBase(NoteVO vo, Note note, Long me) {
        vo.setId(note.getId());
        vo.setContentSummary(summaryOf(note));
        vo.setLogDate(note.getLogDate());
        vo.setOwnerId(note.getOwnerId());
        Map<Long, String> names = userNames(Set.of(note.getOwnerId()));
        vo.setOwnerName(names.get(note.getOwnerId()));
        vo.setOwnerDept(deptName(note.getDeptId()));
        vo.setVersion(versionOf(note));
        vo.setCanEdit(authService.canEdit(note, me));
        vo.setCanManage(authService.canManage(note, me));
        vo.setRole(authService.roleOf(note, me));
        vo.setCreatedAt(note.getCreatedAt());
        vo.setUpdatedAt(note.getUpdatedAt());
    }

    private boolean canEdit(Note n, Long me, boolean admin) {
        return !isTrue(n.getDeleted()) && (admin || (me != null && me.equals(n.getOwnerId())));
    }

    private boolean canManage(Note n, Long me, boolean admin) {
        return canEdit(n, me, admin);
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        Set<Long> filtered = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                filtered.add(id);
            }
        }
        if (filtered.isEmpty()) {
            return out;
        }
        userMapper.selectBatchIds(filtered).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Department d = departmentMapper.selectById(deptId);
        return d == null ? null : d.getName();
    }

    public List<NoteAttachmentVO> attachmentVOs(Note note, Long me, boolean admin) {
        List<NoteAttachmentRef> refs = attachmentRefMapper.selectList(
                Wrappers.<NoteAttachmentRef>lambdaQuery()
                        .eq(NoteAttachmentRef::getNoteId, note.getId())
                        .orderByAsc(NoteAttachmentRef::getSort)
                        .orderByAsc(NoteAttachmentRef::getId));
        if (refs.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        refs.forEach(r -> ids.add(r.getAttachmentId()));
        Map<Long, Attachment> files = new HashMap<>();
        attachmentMapper.selectBatchIds(ids).forEach(a -> files.put(a.getId(), a));
        Set<Long> creators = new HashSet<>();
        files.values().forEach(a -> creators.add(a.getCreatedBy()));
        Map<Long, String> names = userNames(creators);
        boolean canRemoveRef = authService.canManage(note, me);

        List<NoteAttachmentVO> out = new ArrayList<>();
        for (NoteAttachmentRef r : refs) {
            Attachment a = files.get(r.getAttachmentId());
            if (a == null) {
                continue;
            }
            boolean canDelete = admin || (a.getCreatedBy() != null && a.getCreatedBy().equals(me));
            String prefix = "/notes/" + note.getId() + "/attachments/" + a.getId();
            out.add(new NoteAttachmentVO(r.getId(), a.getId(), a.getFileName(), a.getSize(),
                    ContentTypes.of(a.getFileName()),
                    prefix + "/download",
                    prefix + "/preview",
                    a.getCreatedBy(), names.get(a.getCreatedBy()), a.getCreatedAt(),
                    canRemoveRef, canDelete));
        }
        return out;
    }

    /* ==================== 工具 ==================== */

    /**
     * 摘要：先剥离 HTML / Markdown 标记，再取前 200 码位（不补省略号）。
     *
     * <p>直接截断原文会把 HTML 标签或 {@code ##}/{@code **}/{@code ```} 露到卡片上，
     * 甚至把代码围栏切成两半；因此截断前先走 {@link MarkdownText#plain}。
     */
    public static String summary(String content) {
        if (content == null) {
            return null;
        }
        String plain = MarkdownText.plain(content);
        if (plain.codePointCount(0, plain.length()) <= SUMMARY_LIMIT) {
            return plain;
        }
        int end = plain.offsetByCodePoints(0, SUMMARY_LIMIT);
        return plain.substring(0, end);
    }

    /** 按周期取摘要：周记录先剥掉 7 个日期一级标题，正文为空时返回空串（前端展示「本周暂无内容」）。 */
    private static String summaryOf(Note note) {
        if (Note.PERIOD_WEEK.equals(note.getPeriodType())) {
            String body = H1_BLOCK.matcher(note.getContent() == null ? "" : note.getContent()).replaceAll(" ");
            String plain = summary(body);
            return plain == null ? "" : plain.trim();
        }
        return summary(note.getContent());
    }

    private Long versionOf(Note note) {
        return note.getVersion() == null ? null : note.getVersion().longValue();
    }

    private boolean isTrue(Integer v) {
        return v != null && v == 1;
    }

    /** 供创造/更新后返回详情（同一事务内可直接读）。 */
    public Instant now() {
        return Instant.now();
    }

    static LocalDate today() {
        return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    }
}
