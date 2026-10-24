package com.bbtc.bluebird.modules.file.dto;

/**
 * 附件视图。
 *
 * <p>{@code downloadUrl} 为上下文相关相对路径（仅作路径引用，前端走 axios blob）；
 * {@code canDelete} = 可物理删除（创建者/ADMIN，不含引用保护）；
 * {@code canRemoveFromTask} = task 附件删除按钮口径（{@code assertWritable && (createdBy==me||isAdmin)}），
 * 仅 task 附件上下文返回。
 */
public record FileVO(
        Long id,
        String fileName,
        Long size,
        String md5,
        String downloadUrl,
        Long createdBy,
        boolean canDelete,
        Boolean canRemoveFromTask) {
}
