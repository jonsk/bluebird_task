package com.bbtc.bluebird.modules.note.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.util.List;

/**
 * 更新日志请求（PATCH 语义）。
 *
 * <p>字段为 {@code null} 表示「不改」；{@code tagIds=[]} 表示清空；{@code visibility} 省略不变。
 */
public record NoteUpdateReq(
        String content,
        LocalDate logDate,
        String visibility,
        List<Long> tagIds,
        Long version) {

    /** 显式区分「未传 tagIds」(null) 与「传空数组」([])。 */
    public boolean tagIdsPresent() {
        return tagIds != null;
    }

    @JsonIgnore
    public boolean isEmptyPayload() {
        return content == null && logDate == null && visibility == null && tagIds == null;
    }
}
