package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import com.bbtc.bluebird.modules.note.dto.NoteCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteUpdateReq;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteRelationMapper;
import com.bbtc.bluebird.modules.org.application.CurrentUserDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** 日志写操作：创建/更新/删除/置顶 + 字段级权限 + 级联清理。 */
@Service
@RequiredArgsConstructor
public class NoteCommandService {

    private static final int CONTENT_LIMIT = 20_000;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final NoteMapper noteMapper;
    private final NoteAttachmentRefMapper attachmentRefMapper;
    private final NoteRelationMapper relationMapper;
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
        note.setPinned(0);
        note.setOwnerId(me);
        Long deptId = currentUserDeptService.deptIdOf(me);
        note.setDeptId(deptId);
        note.setVersion(0);
        note.setDeleted(0);
        noteMapper.insert(note);

        return queryService.detail(note.getId());
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
        // 级联清理（日志为个人记录，无跨人授权关系需维护）
        attachmentRefMapper.delete(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getNoteId, note.getId()));
        relationMapper.delete(Wrappers.<NoteRelation>lambdaQuery()
                .eq(NoteRelation::getSourceNoteId, note.getId()));
        relationMapper.delete(Wrappers.<NoteRelation>lambdaQuery()
                .eq(NoteRelation::getTargetType, NoteRelation.TARGET_NOTE)
                .eq(NoteRelation::getTargetId, note.getId()));
        noteMapper.deleteById(note.getId());
    }

    @Transactional
    public void pin(Long id, boolean pinned) {
        Note note = authService.requireManageable(id);
        note.setPinned(pinned ? 1 : 0);
        noteMapper.updateById(note);
    }

    /* ==================== 校验 ==================== */

    private void validateContent(String content) {
        if (content == null) {
            throw HttpStatusException.badRequest("content 不能为空");
        }
        if (content.codePointCount(0, content.length()) > CONTENT_LIMIT) {
            throw new BusinessException(ErrorCode.NOTE_CONTENT_TOO_LONG);
        }
    }
}
