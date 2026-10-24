package com.bbtc.bluebird.common.spi;

/**
 * 附件引用保护扩展点（避免 file → note/task 反向依赖）。
 *
 * <p>{@code FileService.delete()} 通过全部实现判断附件是否仍被 note/task 引用；
 * 任一被引用即拒绝物理删除。
 */
public interface AttachmentReferenceGuard {

    /** 是否被 note 引用（{@code note_attachment_ref} + note 未删除）。 */
    boolean referencedByNote(Long attachmentId);

    /** 是否被 task 引用（{@code attachment.task_id} 非空）。 */
    boolean referencedByTask(Long attachmentId);
}
