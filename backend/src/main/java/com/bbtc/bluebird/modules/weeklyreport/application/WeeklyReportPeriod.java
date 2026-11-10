package com.bbtc.bluebird.modules.weeklyreport.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 周报周期：周一 00:00 至周日 23:59（按日期字符串比较日志 log_date）。 */
public record WeeklyReportPeriod(String start, String end) {

    public static WeeklyReportPeriod of(LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeeklyReportPeriod(monday.toString(), monday.plusDays(6).toString());
    }
}
