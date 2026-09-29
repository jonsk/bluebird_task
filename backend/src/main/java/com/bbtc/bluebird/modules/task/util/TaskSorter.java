package com.bbtc.bluebird.modules.task.util;

import com.bbtc.bluebird.modules.task.dto.TaskVO;

import java.time.Instant;
import java.util.Comparator;

/**
 * 任务排序（02 §4.4）：逾期 &gt; 临期 &gt; 普通 &gt; 已完成，同级按 due_at 升序。
 */
public final class TaskSorter {

    private static final long NEAR_MS = 24L * 3600 * 1000;

    private TaskSorter() {
    }

    public static Comparator<TaskVO> comparator(Instant now) {
        return Comparator
                .comparingInt((TaskVO t) -> rank(t, now))
                .thenComparing(t -> t.getDueAt() == null ? Instant.MAX : t.getDueAt());
    }

    private static int rank(TaskVO t, Instant now) {
        if (Boolean.TRUE.equals(t.getCompleted())) {
            return 3;
        }
        if (t.getDueAt() == null) {
            return 2;
        }
        if (t.getDueAt().isBefore(now)) {
            return 0;
        }
        if (t.getDueAt().toEpochMilli() - now.toEpochMilli() <= NEAR_MS) {
            return 1;
        }
        return 2;
    }
}
