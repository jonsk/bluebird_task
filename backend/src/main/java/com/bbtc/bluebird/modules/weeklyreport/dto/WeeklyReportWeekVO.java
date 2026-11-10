package com.bbtc.bluebird.modules.weeklyreport.dto;

/**
 * 左侧「按周生成」列表项。
 *
 * <p>只收录「当前周及之前、且至少有 1 条日志」的周次；该周还没有周报时
 * {@code reportId}/{@code status} 为 null，用户选中后可直接生成。
 */
public record WeeklyReportWeekVO(
        String weekStart,
        String weekEnd,
        long noteCount,
        Long reportId,
        String status,
        String errorMessage
) {
}
