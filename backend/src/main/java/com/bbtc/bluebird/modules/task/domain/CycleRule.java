package com.bbtc.bluebird.modules.task.domain;

import java.time.Instant;
import java.util.List;

/**
 * 周期规则（TEXT 存 JSON，02 §4.3）。{@code count} 与 {@code until} 互斥。
 */
public record CycleRule(
        String freq,
        Integer interval,
        Instant dtstart,
        List<String> byDay,
        Integer count,
        String until,
        String tz) {
}
