package com.bbtc.bluebird.modules.note.dto;

import java.time.LocalDate;

/**
 * 更新工作日志入参（PATCH 语义，字段为 {@code null} 表示不修改）。
 *
 * <p>日志日期在创建时固定，编辑不可修改：携带 {@code logDate} 会返回 HTTP 400。
 */
public record NoteUpdateReq(
        String content,
        String visibility,
        LocalDate logDate,
        Long version) {

    /** 是否显式携带了 logDate（非 null 即视为试图改日期）。 */
    public boolean logDatePresent() {
        return logDate != null;
    }
}
