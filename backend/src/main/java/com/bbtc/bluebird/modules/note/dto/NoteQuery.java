package com.bbtc.bluebird.modules.note.dto;

import java.time.LocalDate;

/**
 * 列表查询条件（由 Controller 从请求参数构造）。
 *
 * @param scope     mine|subordinate|visible|all
 * @param start/end log_date 范围（与 date 互斥）
 * @param date      指定某天
 * @param sort      log_date_desc|log_date_asc|created_desc|updated_desc
 */
public record NoteQuery(
        String scope,
        String keyword,
        Boolean pinned,
        LocalDate start,
        LocalDate end,
        LocalDate date,
        String sort,
        long page,
        long size) {
}
