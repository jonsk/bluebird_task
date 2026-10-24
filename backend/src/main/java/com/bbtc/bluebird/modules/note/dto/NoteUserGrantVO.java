package com.bbtc.bluebird.modules.note.dto;

import java.time.Instant;

/** 授权列表行（direction 与 can* 均由后端按当前用户计算）。 */
public record NoteUserGrantVO(
        Long id,
        Long grantorId,
        String grantorName,
        Long granteeId,
        String granteeName,
        String role,
        String status,
        String mode,
        /** outgoing | incoming | none（仅行内展示，不参与 Tab 过滤） */
        String direction,
        Long createdBy,
        String createdByName,
        Instant createdAt,
        Instant approvedAt,
        Long approvedBy,
        Instant revokedAt,
        Long revokedBy,
        boolean canApprove,
        boolean canReject,
        boolean canUpgrade,
        boolean canRevoke) {
}
