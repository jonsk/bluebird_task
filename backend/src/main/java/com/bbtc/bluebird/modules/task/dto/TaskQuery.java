package com.bbtc.bluebird.modules.task.dto;

/**
 * 任务列表查询（02 §4.4/§4.5）。
 *
 * <p>{@code menuId} 按自定义栏条目过滤（与 {@code scope} 叠加）。
 */
public record TaskQuery(
        String scope,
        String keyword,
        boolean subordinate,
        String date,
        Long menuId,
        long page,
        long size) {
}
