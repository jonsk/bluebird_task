package com.bbtc.bluebird.modules.task.dto;

/** 各视图计数（键名映射旧系统 join→joined、do→assigned）。 */
public record CountVO(long day, long week, long joined, long assigned, long collect, long all) {
}
