package com.bbtc.bluebird.modules.note.dto;

import java.time.LocalDate;

/**
 * 列表查询条件（由 Controller 解析参数后构造）。
 *
 * @param scope      mine|shared|explore|internal|subordinate|visible|all
 * @param tagId      mine scope 用（标签 id）
 * @param tagName    其余 scope 用（标签名）
 * @param archived   true 仅 mine 有意义；ADMIN 可配 scope=all&archived=true
 * @param start/end  log_date 范围（与 date 互斥）
 * @param date       指定某天
 * @param sort       log_date_desc|log_date_asc|created_desc|updated_desc
 */
public record NoteQuery(
        String scope,
        Long tagId,
        String tagName,
        String keyword,
        String visibility,
        Boolean pinned,
        Boolean archived,
        LocalDate start,
        LocalDate end,
        LocalDate date,
        String sort,
        long page,
        long size) {
}
