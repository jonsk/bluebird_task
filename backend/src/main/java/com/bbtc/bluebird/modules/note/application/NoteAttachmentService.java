package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.file.application.FileService;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.dto.NoteAttachmentVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 日志附件：关联/列出/移除/下载。
 *
 * <p>写操作先 {@link NoteAuthService#assertWritable}（归档时 EDITOR 不可写），
 * 再校验附件 {@code assertCanBind}（创建者/ADMIN）；下载按 note 可见性 + 关联存在。
 */
@Service
@RequiredArgsConstructor
public class NoteAttachmentService {

    private final NoteAttachmentRefMapper refMapper;
    private final AttachmentMapper attachmentMapper;
    private final NoteAuthService authService;
    private final NoteQueryService queryService;
    private final FileService fileService;

    @Transactional
    public NoteAttachmentVO bind(Long noteId, Long attachmentId) {
        Note note = authService.requireVisible(noteId);
        Long me = UserContext.currentUserId();
        authService.assertWritable(note, me);

        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !fileService.canBind(attachmentId, me)) {
            throw HttpStatusException.forbidden();
        }
        NoteAttachmentRef existing = refMapper.selectOne(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getNoteId, noteId)
                .eq(NoteAttachmentRef::getAttachmentId, attachmentId)
                .last("LIMIT 1"));
        if (existing == null) {
            NoteAttachmentRef ref = new NoteAttachmentRef();
            ref.setNoteId(noteId);
            ref.setAttachmentId(attachmentId);
            ref.setSort((int) Instant.now().getEpochSecond());
            refMapper.insert(ref);
        }
        return findVo(note, attachmentId);
    }

    public List<NoteAttachmentVO> list(Long noteId) {
        Note note = authService.requireVisible(noteId);
        return queryService.attachmentVOs(note, UserContext.currentUserId(), authService.isAdmin());
    }

    @Transactional
    public void remove(Long noteId, Long attachmentId) {
        Note note = authService.requireManageable(noteId);
        NoteAttachmentRef ref = refMapper.selectOne(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getNoteId, noteId)
                .eq(NoteAttachmentRef::getAttachmentId, attachmentId)
                .last("LIMIT 1"));
        if (ref == null) {
            throw HttpStatusException.notFound();
        }
        refMapper.deleteById(ref.getId());
    }

    public FileService.Download download(Long noteId, Long attachmentId) {
        Note note = authService.requireVisible(noteId);
        Long cnt = refMapper.selectCount(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getNoteId, noteId)
                .eq(NoteAttachmentRef::getAttachmentId, attachmentId));
        if (cnt == null || cnt == 0) {
            throw HttpStatusException.notFound();
        }
        return fileService.load(attachmentId);
    }

    private NoteAttachmentVO findVo(Note note, Long attachmentId) {
        return queryService.attachmentVOs(note, UserContext.currentUserId(), authService.isAdmin())
                .stream()
                .filter(a -> a.attachmentId().equals(attachmentId))
                .findFirst()
                .orElseThrow(HttpStatusException::notFound);
    }
}
