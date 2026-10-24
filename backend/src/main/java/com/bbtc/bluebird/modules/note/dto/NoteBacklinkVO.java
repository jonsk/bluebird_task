package com.bbtc.bluebird.modules.note.dto;

import java.time.Instant;

/** 谁引用了我（source 侧；与 NoteRelationVO 语义不同，不可复用）。 */
public record NoteBacklinkVO(
        Long relationId,
        Long sourceNoteId,
        String sourceSummary,
        Long sourceOwnerId,
        String sourceOwnerName,
        Instant createdAt,
        Long createdBy,
        String createdByName) {
}
