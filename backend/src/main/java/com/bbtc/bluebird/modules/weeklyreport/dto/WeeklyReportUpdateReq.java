package com.bbtc.bluebird.modules.weeklyreport.dto;

import jakarta.validation.constraints.Size;

/** 手动保存周报正文（定稿）。version 用于乐观锁。 */
public record WeeklyReportUpdateReq(
        @Size(max = 200000) String contentHtml,
        Integer version) {
}
