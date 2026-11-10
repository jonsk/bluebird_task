package com.bbtc.bluebird.modules.weeklyreport.dto;

import java.time.Instant;
import java.util.List;

/** 周报详情。 */
public record WeeklyReportVO(
        Long id,
        Long ownerId,
        String weekStart,
        String weekEnd,
        String status,
        String contentHtml,
        String aiResultJson,
        List<Long> sourceNoteIds,
        String modelName,
        String errorMessage,
        Integer inputTokens,
        Integer outputTokens,
        Instant generatedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
