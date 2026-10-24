package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.spi.AttachmentReferenceGuard;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** note 侧引用保护：被未删除的日志引用即不可物理删除。 */
@Component
@RequiredArgsConstructor
public class NoteAttachmentGuard implements AttachmentReferenceGuard {

    private final NoteAttachmentRefMapper refMapper;
    private final NoteMapper noteMapper;

    @Override
    public boolean referencedByNote(Long attachmentId) {
        var refs = refMapper.selectList(Wrappers.<NoteAttachmentRef>lambdaQuery()
                .eq(NoteAttachmentRef::getAttachmentId, attachmentId));
        for (NoteAttachmentRef r : refs) {
            Note note = noteMapper.selectById(r.getNoteId());
            if (note != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean referencedByTask(Long attachmentId) {
        return false;
    }
}
