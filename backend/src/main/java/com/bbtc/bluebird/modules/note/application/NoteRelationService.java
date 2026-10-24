package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import com.bbtc.bluebird.modules.note.dto.NoteBacklinkVO;
import com.bbtc.bluebird.modules.note.dto.NoteRelationCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteRelationVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteRelationMapper;
import com.bbtc.bluebird.modules.task.application.TaskAuthService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 引用：note↔note、note→task（幂等、禁止自引用、目标可见性校验）。 */
@Service
@RequiredArgsConstructor
public class NoteRelationService {

    private final NoteRelationMapper relationMapper;
    private final NoteMapper noteMapper;
    private final TaskMapper taskMapper;
    private final NoteAuthService authService;
    private final NoteQueryService queryService;
    private final TaskAuthService taskAuthService;

    @Transactional
    public NoteRelationVO add(Long noteId, NoteRelationCreateReq req) {
        Note note = authService.requireVisible(noteId);
        Long me = UserContext.currentUserId();
        authService.assertWritable(note, me);

        String type = req.targetType();
        if (!NoteRelation.TARGET_NOTE.equals(type) && !NoteRelation.TARGET_TASK.equals(type)) {
            throw HttpStatusException.badRequest("非法 targetType");
        }
        if (NoteRelation.TARGET_NOTE.equals(type) && noteId.equals(req.targetId())) {
            throw HttpStatusException.badRequest("不能引用自己");
        }
        validateTarget(type, req.targetId(), me);

        NoteRelation existing = relationMapper.selectOne(Wrappers.<NoteRelation>lambdaQuery()
                .eq(NoteRelation::getSourceNoteId, noteId)
                .eq(NoteRelation::getTargetType, type)
                .eq(NoteRelation::getTargetId, req.targetId())
                .last("LIMIT 1"));
        if (existing != null) {
            return findVo(noteId, existing.getId());
        }
        NoteRelation r = new NoteRelation();
        r.setSourceNoteId(noteId);
        r.setTargetType(type);
        r.setTargetId(req.targetId());
        r.setCreatedAt(Instant.now());
        r.setCreatedBy(me);
        relationMapper.insert(r);
        return findVo(noteId, r.getId());
    }

    public List<NoteRelationVO> list(Long noteId) {
        Note note = authService.requireVisible(noteId);
        return queryService.toDetail(note).getRelations();
    }

    public List<NoteBacklinkVO> backlinks(Long noteId) {
        Note note = authService.requireVisible(noteId);
        return queryService.toDetail(note).getBacklinks();
    }

    @Transactional
    public void delete(Long noteId, Long relationId) {
        Note note = authService.requireManageable(noteId);
        NoteRelation r = relationMapper.selectById(relationId);
        if (r == null || !note.getId().equals(r.getSourceNoteId())) {
            throw HttpStatusException.notFound();
        }
        relationMapper.deleteById(relationId);
    }

    private void validateTarget(String type, Long targetId, Long me) {
        if (NoteRelation.TARGET_NOTE.equals(type)) {
            Note target = noteMapper.selectById(targetId);
            if (target == null || !authService.isVisible(target, me)) {
                throw new BusinessException(ErrorCode.NOTE_RELATION_TARGET_NOT_FOUND);
            }
            return;
        }
        Task task = taskMapper.selectById(targetId);
        if (task == null || !taskAuthService.isVisible(task, me)) {
            throw new BusinessException(ErrorCode.NOTE_RELATION_TARGET_NOT_FOUND);
        }
    }

    private NoteRelationVO findVo(Long noteId, Long relationId) {
        return queryService.toDetail(authService.requireVisible(noteId)).getRelations().stream()
                .filter(r -> r.id().equals(relationId))
                .findFirst()
                .orElseThrow(HttpStatusException::notFound);
    }
}
