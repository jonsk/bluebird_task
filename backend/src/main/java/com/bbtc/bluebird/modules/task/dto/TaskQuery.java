package com.bbtc.bluebird.modules.task.dto;

/** 任务列表查询（02 §4.4/§4.5）。 */
public record TaskQuery(
        String scope,
        String keyword,
        boolean subordinate,
        String date,
        long page,
        long size) {
}
