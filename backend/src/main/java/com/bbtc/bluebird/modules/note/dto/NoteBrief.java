package com.bbtc.bluebird.modules.note.dto;

import com.bbtc.bluebird.modules.task.dto.TagVO;

import java.util.List;

/** 列表卡片视图（只给摘要，不给全文）。 */
public record NoteBrief(
        Long id,
        String contentSummary,
        String logDate,
        String visibility,
        boolean pinned,
        boolean archived,
        Long ownerId,
        String ownerName,
        List<TagVO> tags,
        boolean canEdit,
        boolean canManage,
        Long version) {
}
