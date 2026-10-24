package com.bbtc.bluebird.modules.note.dto;

import java.time.Instant;

/**
 * note 附件视图。
 *
 * <p>{@code id} = 关联 id（{@code note_attachment_ref.id}），删除时用 {@code attachmentId}；
 * {@code createdAt/createdBy} = 附件上传时间/上传者（非关联时间）。
 */
public record NoteAttachmentVO(
        Long id,
        Long attachmentId,
        String fileName,
        Long size,
        String contentType,
        String downloadUrl,
        Long createdBy,
        String createdByName,
        Instant createdAt,
        boolean canRemoveRef,
        boolean canDelete) {
}
