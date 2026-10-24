package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import com.bbtc.bluebird.modules.note.domain.NoteTag;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import com.bbtc.bluebird.modules.note.domain.Visibility;
import com.bbtc.bluebird.modules.note.dto.NoteCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteUpdateReq;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteRelationMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteTagMapper;
import com.bbtc.bluebird.modules.org.application.CurrentUserDeptService;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 日志写操作：创建/更新/删除/置顶/归档 + 字段级权限 + dept_id 刷新 + 级联清理。 */
@Service
@RequiredArgsConstructor
public class NoteCommandService {

    private static final int CONTENT_LIMIT = 20_000;
    private static final int LOG_DATE_FUTURE_DAYS = 7;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteAttachmentRefMapper attachmentRefMapper;
    private final NoteRelationMapper relationMapper;
    private final TagMapper tagMapper;
    private final NoteAuthService authService;
    private final NoteQueryService queryService;
    private final CurrentUserDeptService currentUserDeptService;

    @Transactional
    public NoteDetailVO create(NoteCreateReq req) {
        Long me = UserContext.currentUserId();
        validateContent(req.content());
        validateLogDate(req.logDate());
        Visibility visibility = req.visibility() == null
                ? Visibility.PRIVATE : parseVisibility(req.visibility());
        validateTagOwnership(req.tagIds(), me);

        Long deptId = currentUserDeptService.deptIdOf(me);
        if (visibility == Visibility.INTERNAL && deptId == null) {
            throw new BusinessException(ErrorCode.NOTE_DEPT_REQUIRED);
        }

        Note note = new Note();
        note.setContent(req.content());
        note.setLogDate(req.logDate().toString());
        note.setVisibility(visibility.name());
        note.setPinned(0);
        note.setArchived(0);
        note.setOwnerId(me);
        note.setDeptId(deptId);
        note.setVersion(0);
        note.setDeleted(0);
        noteMapper.insert(note);

        replaceTags(note.getId(), req.tagIds());
        return queryService.detail(note.getId());
    }

    @Transactional
    public NoteDetailVO update(Long id, NoteUpdateReq req) {
        Long me = UserContext.currentUserId();
        Note note = authService.requireExisting(id);
        authService.assertWritable(note, me);
        boolean admin = authService.isAdmin();
        boolean owner = me.equals(note.getOwnerId());
        String role = authService.grantRole(note, me);
        boolean editor = !owner && !admin && NoteUserGrant.EDITOR.equals(role);
        if (!owner && !admin && !editor) {
            throw HttpStatusException.forbidden();
        }

        if (req.content() != null) {
            validateContent(req.content());
        }
        if (req.logDate() != null) {
            validateLogDate(req.logDate());
        }

        Visibility newVisibility = null;
        if (req.visibility() != null) {
            newVisibility = parseVisibility(req.visibility());
            if (editor && !newVisibility.name().equals(note.getVisibility())) {
                throw new BusinessException(ErrorCode.NOTE_FIELD_FORBIDDEN, "无权修改可见性");
            }
        }
        if (req.tagIds() != null && editor && !sameTagIds(id, req.tagIds())) {
            throw new BusinessException(ErrorCode.NOTE_FIELD_FORBIDDEN, "无权修改标签");
        }
        if (req.tagIds() != null) {
            validateTagOwnership(req.tagIds(), note.getOwnerId());
        }

        // dept_id 刷新：仅在「非 INTERNAL → INTERNAL」时刷新；其余保持快照
        if (newVisibility == Visibility.INTERNAL
                && !Visibility.INTERNAL.name().equals(note.getVisibility())) {
            Long ownerDept = currentUserDeptService.deptIdOf(note.getOwnerId());
            if (ownerDept == null) {
                throw new BusinessException(ErrorCode.NOTE_DEPT_REQUIRED);
            }
            note.setDeptId(ownerDept);
        }

        if (req.content() != null) {
            note.setContent(req.content());
        }
        if (req.logDate() != null) {
            note.setLogDate(req.logDate().toString());
        }
        if (newVisibility != null) {
            note.setVisibility(newVisibility.name());
        }
        if (req.version() == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "缺少 version（乐观锁）");
        }
        note.setVersion(req.version().intValue());
        if (noteMapper.updateById(note) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (req.tagIds() != null) {
            replaceTags(id, req.tagIds());
        }
        return queryService.detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Note note = authService.requireManageable(id);
        // 级联清理（不清理 note_user_grant：用户级全量授权，删单条日志不应破坏授权）
        noteTagMapper.delete(Wrappers.<NoteTag>lambdaQuery().eq(NoteTag::getNoteId, note.getId()));
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

    @Transactional
    public void archive(Long id, boolean archived) {
        Note note = authService.requireManageable(id);
        note.setArchived(archived ? 1 : 0);
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

    private void validateLogDate(LocalDate logDate) {
        if (logDate == null) {
            throw HttpStatusException.badRequest("logDate 不能为空");
        }
        if (logDate.isAfter(LocalDate.now(ZONE).plusDays(LOG_DATE_FUTURE_DAYS))) {
            throw new BusinessException(ErrorCode.NOTE_LOG_DATE_INVALID);
        }
    }

    private Visibility parseVisibility(String value) {
        Visibility v = Visibility.of(value);
        if (v == null) {
            throw HttpStatusException.badRequest("非法 visibility");
        }
        return v;
    }

    private void validateTagOwnership(List<Long> tagIds, Long ownerId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new HashSet<>(tagIds);
        List<Tag> tags = tagMapper.selectBatchIds(distinct);
        if (tags.size() != distinct.size()) {
            throw new BusinessException(ErrorCode.NOTE_TAG_INVALID);
        }
        for (Tag t : tags) {
            if (!ownerId.equals(t.getOwnerId())) {
                throw new BusinessException(ErrorCode.NOTE_TAG_INVALID);
            }
        }
    }

    private boolean sameTagIds(Long noteId, List<Long> tagIds) {
        Set<Long> current = new HashSet<>();
        noteTagMapper.selectList(Wrappers.<NoteTag>lambdaQuery().eq(NoteTag::getNoteId, noteId))
                .forEach(t -> current.add(t.getTagId()));
        return current.equals(new HashSet<>(tagIds));
    }

    private void replaceTags(Long noteId, List<Long> tagIds) {
        noteTagMapper.delete(Wrappers.<NoteTag>lambdaQuery().eq(NoteTag::getNoteId, noteId));
        if (tagIds == null) {
            return;
        }
        for (Long tagId : new java.util.LinkedHashSet<>(tagIds)) {
            NoteTag nt = new NoteTag();
            nt.setNoteId(noteId);
            nt.setTagId(tagId);
            noteTagMapper.insert(nt);
        }
    }
}
