package com.bbtc.bluebird.common.util;

/**
 * 主键生成：snowflake（单实例固定 workerId=0；SQLite 单写者，ADR-016）。
 *
 * <p>含时钟回拨兜底（02 §1.10 R7）：回拨小于阈值时自旋等待，超时 fail-fast。
 */
public final class IdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long MAX_BACKWARD_WAIT_MS = 5000L;

    private static final long WORKER_ID = 0L;

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private IdGenerator() {
    }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_BACKWARD_WAIT_MS) {
                throw new IllegalStateException("Clock moved backwards beyond threshold: " + offset + "ms");
            }
            // 乐观等待时钟追平
            while ((timestamp = System.currentTimeMillis()) < lastTimestamp) {
                Thread.onSpinWait();
            }
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (WORKER_ID << WORKER_ID_SHIFT) | sequence;
    }

    private static long tilNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
