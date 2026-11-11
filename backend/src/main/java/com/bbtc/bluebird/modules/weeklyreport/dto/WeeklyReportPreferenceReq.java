package com.bbtc.bluebird.modules.weeklyreport.dto;

import jakarta.validation.constraints.Size;

/** 周报个人偏好（仅补充规则）。 */
public record WeeklyReportPreferenceReq(
        @Size(max = 20, message = "写作风格不能超过 20 个字符") String style,
        @Size(max = 200, message = "关注点不能超过 200 个字符") String focus,
        @Size(max = 2000, message = "补充规则不能超过 2000 个字符") String customRules) {
}
