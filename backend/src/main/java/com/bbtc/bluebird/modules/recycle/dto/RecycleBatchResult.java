package com.bbtc.bluebird.modules.recycle.dto;

import java.util.List;

/**
 * 批量操作结果：成功条数 + 被跳过的条目及原因（逐条容错，不因一条失败整批回滚）。
 */
public record RecycleBatchResult(int affected, List<Skipped> skipped) {

    public record Skipped(String type, Long id, String reason) {
    }

    public static RecycleBatchResult of(int affected, List<Skipped> skipped) {
        return new RecycleBatchResult(affected, skipped);
    }
}
