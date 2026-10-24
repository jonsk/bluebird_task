package com.bbtc.bluebird.modules.task.dto;

/**
 * 附件引用（不含 relativePath，02 §5.6）。
 *
 * <p>{@code downloadUrl} 为 task 专用相对路径；{@code canRemoveFromTask} 由后端按当前用户+task 计算。
 */
public record FileRef(
        Long id,
        String fileName,
        Long size,
        String downloadUrl,
        Long createdBy,
        boolean canDelete,
        Boolean canRemoveFromTask) {
}
