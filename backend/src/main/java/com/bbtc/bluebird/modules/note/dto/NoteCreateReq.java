package com.bbtc.bluebird.modules.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** 创建日志请求。{@code logDate} 严格 ISO 解析（不可解析 → HTTP 400）。 */
public record NoteCreateReq(
        @NotBlank String content,
        @NotNull LocalDate logDate,
        String visibility,
        List<Long> tagIds) {
}
