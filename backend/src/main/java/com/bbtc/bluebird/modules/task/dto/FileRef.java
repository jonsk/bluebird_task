package com.bbtc.bluebird.modules.task.dto;

/** 附件引用（不含 relativePath，02 §5.6）。 */
public record FileRef(Long id, String fileName, Long size) {
}
