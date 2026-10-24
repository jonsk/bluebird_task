package com.bbtc.bluebird.modules.note.dto;

import java.time.Instant;

/** 我引用了谁（target 侧）。 */
public record NoteRelationVO(
        Long id,
        String targetType,
        Long targetId,
        String targetSummary,
        Instant createdAt,
        Long createdBy,
        String createdByName) {
}
