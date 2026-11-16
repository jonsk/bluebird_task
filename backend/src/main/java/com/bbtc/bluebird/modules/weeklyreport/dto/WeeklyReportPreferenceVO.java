package com.bbtc.bluebird.modules.weeklyreport.dto;

import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportType;

/** 周报个人偏好。 */
public record WeeklyReportPreferenceVO(
        String style,
        String focus,
        String customRules,
        WeeklyReportType reportType) {
}
