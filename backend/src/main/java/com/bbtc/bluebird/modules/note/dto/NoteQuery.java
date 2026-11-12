package com.bbtc.bluebird.modules.note.dto;

import java.time.LocalDate;

/**
 * 列表查询条件（由 Controller 从请求参数构造）。
 *
 * @param scope     mine|subordinate|visible|all
 * @param period    DAY（工作日志，默认）| WEEK（每周工作记录）
 * @param start/end 周期起点范围（DAY 落在 log_date，WEEK 落在 period_start），与 date 互斥
 * @param date      指定某个周期起点
 * @param sort      log_date_desc|log_date_asc|created_desc|updated_desc
 */
public record NoteQuery(
        String scope,
        String keyword,
        LocalDate start,
        LocalDate end,
        LocalDate date,
        String sort,
        long page,
        long size,
        String period) {

    /** 兼容旧调用：不传 period 即按日日志。 */
    public NoteQuery(String scope, String keyword, LocalDate start, LocalDate end,
                     LocalDate date, String sort, long page, long size) {
        this(scope, keyword, start, end, date, sort, page, size, null);
    }
}
