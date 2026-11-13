package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.RichTextPlain;
import com.bbtc.bluebird.common.util.RichTextGuard;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.dto.NoteCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteUpdateReq;
import com.bbtc.bluebird.modules.note.dto.WeeklyNoteCreateReq;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.org.application.CurrentUserDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

/** 日志写操作：创建/更新/删除 + 字段级权限 + 级联清理。 */
@Service
@RequiredArgsConstructor
public class NoteCommandService {

    private static final int CONTENT_LIMIT = 20_000;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final NoteMapper noteMapper;
    private final NoteAttachmentRefMapper attachmentRefMapper;
    private final NoteAuthService authService;
    private final NoteQueryService queryService;
    private final CurrentUserDeptService currentUserDeptService;

    @Transactional
    public NoteDetailVO create(NoteCreateReq req) {
        Long me = UserContext.currentUserId();
        validateContent(req.content());

        Note note = new Note();
        note.setContent(req.content());
        // 日志日期不可选：一律记为创建当天（Asia/Shanghai）。
        note.setLogDate(LocalDate.now(ZONE).toString());
        note.setPeriodType(Note.PERIOD_DAY);
        note.setPeriodStart(note.getLogDate());
        note.setOwnerId(me);
        Long deptId = currentUserDeptService.deptIdOf(me);
        note.setDeptId(deptId);
        note.setVersion(0);
        note.setDeleted(0);
        noteMapper.insert(note);

        return queryService.detail(note.getId());
    }

    /**
     * 创建/打开指定周（缺省本周）的每周工作记录。
     *
     * <p>一周一条由 DB 部分唯一索引兜底；这里先查再插，撞并发唯一键时回读已存在那条。
     * 已存在时**不覆盖**原内容，返回现有记录，前端据此转成编辑态（避免另一个标签页把自己写的内容冲掉）。
     *
     * <p>{@code weekStart} 传入时只允许本周之后的周；任意日期都归一化到该周周一。
     *
     * @param req 可空；为空/正文空白时用 7 个日期一级标题作为默认正文
     */
    @Transactional
    public NoteDetailVO createWeekly(WeeklyNoteCreateReq req) {
        Long me = UserContext.currentUserId();
        LocalDate thisMonday = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean explicitWeek = req != null && req.weekStart() != null && !req.weekStart().isBlank();
        LocalDate monday = explicitWeek ? parseWeekStart(req.weekStart()) : thisMonday;
        if (explicitWeek && !monday.isAfter(thisMonday)) {
            throw HttpStatusException.badRequest("只能创建本周之后的每周工作记录");
        }
        String start = monday.toString();

        Note existing = findByWeek(me, start);
        if (existing != null) {
            return queryService.detail(existing.getId());
        }

        String content = req == null || req.content() == null || req.content().isBlank()
                ? weekTemplate(monday) : req.content();
        validateContent(content);

        Note note = new Note();
        note.setContent(content);
        note.setLogDate(start);
        note.setPeriodType(Note.PERIOD_WEEK);
        note.setPeriodStart(start);
        note.setOwnerId(me);
        note.setDeptId(currentUserDeptService.deptIdOf(me));
        note.setVersion(0);
        note.setDeleted(0);
        try {
            noteMapper.insert(note);
        } catch (DuplicateKeyException e) {
            // 并发创建：另一请求刚插入同一周，回读它，保持「一周一条」
            Note race = findByWeek(me, start);
            if (race == null) {
                throw e;
            }
            return queryService.detail(race.getId());
        }
        return queryService.detail(note.getId());
    }

    /** 任意日期 → 该周周一；格式非法直接 400。 */
    private LocalDate parseWeekStart(String raw) {
        try {
            return LocalDate.parse(raw.trim()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeParseException e) {
            throw HttpStatusException.badRequest("weekStart 格式非法（YYYY-MM-DD）：" + raw);
        }
    }

    private Note findByWeek(Long ownerId, String weekStart) {
        return noteMapper.selectOne(Wrappers.<Note>lambdaQuery()
                .eq(Note::getOwnerId, ownerId)
                .eq(Note::getPeriodType, Note.PERIOD_WEEK)
                .eq(Note::getPeriodStart, weekStart)
                .last("LIMIT 1"));
    }

    /** 默认正文：7 个一级标题，日期逐日取自己的年月（跨月/跨年不会写错）；日期之间空两行。 */
    static String weekTemplate(LocalDate monday) {
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < WEEKDAYS.length; i++) {
            if (i > 0) {
                sb.append("<p></p><p></p>");
            }
            LocalDate d = monday.plusDays(i);
            sb.append("<h1>").append(d.getYear()).append('年')
                    .append(d.getMonthValue()).append('月')
                    .append(d.getDayOfMonth()).append("日 ")
                    .append(WEEKDAYS[i]).append("</h1>");
        }
        // 与前端新建态保持一致，末尾留一行可直接输入的位置。
        return sb.append("<p></p>").toString();
    }

    @Transactional
    public NoteDetailVO update(Long id, NoteUpdateReq req) {
        Long me = UserContext.currentUserId();
        Note note = authService.requireExisting(id);
        authService.assertWritable(note, me);
        boolean admin = authService.isAdmin();
        boolean owner = me.equals(note.getOwnerId());
        if (!owner && !admin) {
            throw HttpStatusException.forbidden();
        }

        if (req.logDatePresent()) {
            // 日志日期在建单时固定，编辑不得改动。
            throw HttpStatusException.badRequest("logDate 不可修改（日志日期由创建时间决定）");
        }
        if (req.content() != null) {
            validateContent(req.content());
        }

        if (req.content() != null) {
            note.setContent(req.content());
        }
        if (req.version() == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "缺少 version（乐观锁）");
        }
        note.setVersion(req.version().intValue());
        if (noteMapper.updateById(note) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        return queryService.detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Note note = authService.requireManageable(id);
        // 回收站语义（2026-10-30）：只打逻辑删标记，附件引用 / 引用关系保留到彻底删除，
        // 否则恢复出来的日志会丢掉附件与「引用/被引用」关系。
        noteMapper.markDeleted(note.getId(), Instant.now(), UserContext.currentUserId());
    }

    /**
     * 彻底删除（回收站专用，权限由调用方保证）：级联清理附件引用后物理删行。
     */
    @Transactional
    public void purge(Long id) {
        Note note = noteMapper.selectDeletedById(id);
        if (note == null) {
            return;
        }
        attachmentRefMapper.delete(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getNoteId, note.getId()));
        noteMapper.hardDelete(note.getId());
    }

    /* ==================== 校验 ==================== */

    private void validateContent(String content) {
        if (content == null) {
            throw HttpStatusException.badRequest("content 不能为空");
        }
        // 富文本原样落库，渲染侧靠 Tiptap schema；这里拒绝明确危险载荷，作为第二道防线。
        RichTextGuard.assertSafe(content);
        // 富文本按用户可见文字计数，避免 HTML 标签把 20000 字额度提前吃掉。
        String measured = RichTextPlain.plain(content);
        if (measured.codePointCount(0, measured.length()) > CONTENT_LIMIT) {
            throw new BusinessException(ErrorCode.NOTE_CONTENT_TOO_LONG);
        }
    }
}
