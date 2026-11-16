package com.bbtc.bluebird.modules.weeklyreport.domain;

import java.util.Locale;

/**
 * AI 总结的数据来源类型：一周只保留一份报告，用户选择用每日记录还是每周记录生成。
 *
 * <p>默认「每周工作记录」：周记录本身就是整周复盘，信息密度更高；没有周记录的用户
 * 可以在 AI 生成页面切到「每日工作记录」，也可以由定时任务按保存的偏好跳过。
 */
public enum WeeklyReportType {

    DAILY("每日工作记录"),
    WEEKLY("每周工作记录");

    private final String label;

    WeeklyReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 请求参数解析：空值或非法值统一回落到默认的「每周工作记录」。 */
    public static WeeklyReportType from(String value) {
        if (value == null || value.isBlank()) {
            return WEEKLY;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "DAILY" -> DAILY;
            default -> WEEKLY;
        };
    }
}
