package com.bbtc.bluebird.modules.note.dto;

/** 列表卡片视图（只有摘要，不含全文）。 */
public record NoteBrief(
        Long id,
        String contentSummary,
        String logDate,
        boolean pinned,
        Long ownerId,
        String ownerName,
        boolean canEdit,
        boolean canManage,
        Long version) {
}
