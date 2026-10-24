package com.bbtc.bluebird.modules.task.application;

import com.bbtc.bluebird.common.spi.AttachmentReferenceGuard;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** task 侧引用保护：{@code attachment.task_id} 非空即视为被 task 引用。 */
@Component
@RequiredArgsConstructor
public class TaskAttachmentGuard implements AttachmentReferenceGuard {

    private final AttachmentMapper attachmentMapper;

    @Override
    public boolean referencedByNote(Long attachmentId) {
        return false;
    }

    @Override
    public boolean referencedByTask(Long attachmentId) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        return a != null && a.getTaskId() != null;
    }
}
