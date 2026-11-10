package com.bbtc.bluebird.modules.weeklyreport.dto;

import jakarta.validation.constraints.Size;

/** 周报个人偏好（仅补充规则）。 */
public record WeeklyReportPreferenceReq(
        @Size(max = 20) String style,
        @Size(max = 200) String focus,
        @Size(max = 2000) String customRules) {
}
