package com.bbtc.bluebird.modules.weeklyreport.dto;

/**
 * 生成请求。
 *
 * @param weekStart 任意属于目标周的日期（默认今天），后端归一到周一
 * @param overwrite 已存在草稿/定稿时是否覆盖重生成
 */
public record WeeklyReportGenerateReq(String weekStart, Boolean overwrite) {
}
