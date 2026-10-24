package com.bbtc.bluebird.modules.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 添加引用请求体（targetType 枚举非法 → 400）。 */
public record NoteRelationCreateReq(
        @NotBlank String targetType,
        @NotNull Long targetId) {
}
