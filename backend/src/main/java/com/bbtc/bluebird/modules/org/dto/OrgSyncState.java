package com.bbtc.bluebird.modules.org.dto;

import java.time.Instant;

/**
 * 组织同步运行时状态（进程内单实例，ADR-016）。
 */
public final class OrgSyncState {

    private static volatile boolean running = false;
    private static volatile Instant lastSyncAt;
    private static volatile String lastResult;

    private OrgSyncState() {
    }

    public static boolean running() {
        return running;
    }

    public static void setRunning(boolean value) {
        running = value;
    }

    public static Instant lastSyncAt() {
        return lastSyncAt;
    }

    public static String lastResult() {
        return lastResult;
    }

    public static void record(Instant at, String result) {
        lastSyncAt = at;
        lastResult = result;
    }
}
