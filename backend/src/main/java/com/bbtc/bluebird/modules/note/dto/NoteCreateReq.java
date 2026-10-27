package com.bbtc.bluebird.modules.note.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建工作日志入参。
 *
 * <p>日志日期不可选：服务端固定记为创建当天（Asia/Shanghai），故不接受 {@code logDate}。
 */
public record NoteCreateReq(
        @NotBlank String content,
        String visibility) {
}
