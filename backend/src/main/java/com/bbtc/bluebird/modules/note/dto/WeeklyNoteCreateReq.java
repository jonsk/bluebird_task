package com.bbtc.bluebird.modules.note.dto;

/**
 * 每周工作记录创建入参。
 *
 * <p>{@code weekStart} 可空：缺省为本周；传入时可以是该周任意一天，服务端统一归一化到
 * ISO 周一（Asia/Shanghai）。指定周时只允许本周之后的周。
 */
public record WeeklyNoteCreateReq(
        String content,
        String weekStart) {
}
