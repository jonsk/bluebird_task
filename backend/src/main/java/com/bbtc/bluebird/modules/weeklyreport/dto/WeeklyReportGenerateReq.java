package com.bbtc.bluebird.modules.weeklyreport.dto;

import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportType;

/**
 * 生成请求。
 *
 * @param weekStart 任意属于目标周的日期（默认今天），后端归一到周一
 * @param overwrite 已存在定稿时是否覆盖重生成
 * @param type      本次使用的数据来源；为空时沿用个人偏好（默认每周工作记录）
 */
public record WeeklyReportGenerateReq(String weekStart, Boolean overwrite, WeeklyReportType type) {
}
