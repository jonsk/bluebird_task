package com.bbtc.bluebird.common.model;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 上下文（MDC）。每次请求生成，贯穿日志；响应头 {@code X-Trace-Id}（02 §1.3）。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    public static String generate() {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
