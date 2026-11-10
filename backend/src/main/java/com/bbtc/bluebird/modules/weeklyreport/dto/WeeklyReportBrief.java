package com.bbtc.bluebird.modules.weeklyreport.dto;

import java.time.Instant;

/** 周报列表项。 */
public record WeeklyReportBrief(
        Long id,
        String weekStart,
        String weekEnd,
        String status,
        String summary,
        String errorMessage,
        Instant generatedAt,
        Instant updatedAt) {
}
