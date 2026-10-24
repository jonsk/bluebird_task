package com.bbtc.bluebird.modules.note.dto;

import jakarta.validation.constraints.NotNull;

/** 关联附件请求体（单条绑定；多文件由前端逐个调用）。 */
public record NoteAttachmentBindReq(@NotNull Long attachmentId) {
}
