package com.bbtc.bluebird;

import com.bbtc.bluebird.modules.task.domain.CycleRule;
import com.bbtc.bluebird.modules.task.util.CycleExpander;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 周期规则展开（02 §4.3 R2/M2）。 */
class CycleExpanderTest {

    private Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void dailyExpandsWithinWindow() {
        CycleRule rule = new CycleRule("DAILY", 1, at("2026-10-01T02:00:00Z"), null, null, null, "Asia/Shanghai");
        List<Instant> list = CycleExpander.expand(rule, null, at("2026-10-01T00:00:00Z"), at("2026-10-05T23:59:59Z"), 100);
        assertEquals(5, list.size());
    }

    @Test
    void weeklyByDayHonoursWeekdays() {
        // 2026-01-05 是周一
        CycleRule rule = new CycleRule("WEEKLY", 1, at("2026-01-05T01:00:00Z"),
                List.of("MO", "WE"), null, null, "UTC");
        List<Instant> list = CycleExpander.expand(rule, null, at("2026-01-05T00:00:00Z"), at("2026-01-18T23:59:59Z"), 100);
        assertEquals(List.of(at("2026-01-05T01:00:00Z"), at("2026-01-07T01:00:00Z"),
                at("2026-01-12T01:00:00Z"), at("2026-01-14T01:00:00Z")), list);
    }

    @Test
    void skipsCompletedInstances() {
        CycleRule rule = new CycleRule("WEEKLY", 1, at("2026-01-05T01:00:00Z"),
                List.of("MO", "WE"), null, null, "UTC");
        List<Instant> list = CycleExpander.expand(rule, at("2026-01-07T01:00:00Z"),
                at("2026-01-05T00:00:00Z"), at("2026-01-18T23:59:59Z"), 100);
        assertEquals(2, list.size());
        assertTrue(list.get(0).isAfter(at("2026-01-07T01:00:00Z")));
    }

    @Test
    void countTerminates() {
        CycleRule rule = new CycleRule("DAILY", 1, at("2026-01-05T01:00:00Z"), null, 2, null, "UTC");
        List<Instant> list = CycleExpander.expand(rule, null, at("2026-01-01T00:00:00Z"), at("2026-01-31T00:00:00Z"), 100);
        assertEquals(2, list.size());
    }

    @Test
    void untilTerminates() {
        CycleRule rule = new CycleRule("DAILY", 1, at("2026-01-05T01:00:00Z"), null, null, "2026-01-08", "UTC");
        List<Instant> list = CycleExpander.expand(rule, null, at("2026-01-01T00:00:00Z"), at("2026-01-31T00:00:00Z"), 100);
        assertEquals(4, list.size());
    }

    @Test
    void firstNextReturnsEarliestFuture() {
        CycleRule rule = new CycleRule("DAILY", 1, at("2026-01-05T01:00:00Z"), null, null, null, "UTC");
        Instant next = CycleExpander.firstNext(rule, at("2026-01-05T01:00:00Z"), at("2026-01-10T00:00:00Z"),
                at("2026-12-31T00:00:00Z"));
        assertEquals(at("2026-01-10T01:00:00Z"), next);
    }
}
