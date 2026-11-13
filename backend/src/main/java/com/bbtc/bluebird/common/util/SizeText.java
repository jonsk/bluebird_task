package com.bbtc.bluebird.common.util;

import java.util.Locale;

/**
 * 文件大小文本解析（如 {@code 50MB} / {@code 2GB}）。
 *
 * <p>存储上限与上传校验都需要同一套口径：管理页保存的字符串、application.yml 的默认值、
 * 运行时读取的配置都走这里，避免两处各写一份 parse 逻辑导致行为漂移。
 */
public final class SizeText {

    /** 解析失败或非正数时返回的哨兵值。 */
    public static final long INVALID = -1L;

    private SizeText() {
    }

    /**
     * 解析大小文本；非法、非正数或溢出时返回 {@link #INVALID}。
     * 支持的写法：{@code 512B}、{@code 50KB}、{@code 50MB}、{@code 2GB}（大小写不敏感），
     * 纯数字按字节处理。
     */
    public static long parseBytesOrInvalid(String value) {
        if (value == null || value.isBlank()) {
            return INVALID;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        long mul = 1;
        if (v.endsWith("KB")) {
            mul = 1024L;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("MB")) {
            mul = 1024L * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("GB")) {
            mul = 1024L * 1024 * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("B")) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            long n = Long.parseLong(v.trim());
            if (n <= 0) {
                return INVALID;
            }
            return Math.multiplyExact(n, mul);
        } catch (NumberFormatException | ArithmeticException e) {
            return INVALID;
        }
    }

    /** 解析大小文本；非法时回落到 {@code fallback}。 */
    public static long parseBytes(String value, long fallback) {
        long parsed = parseBytesOrInvalid(value);
        return parsed == INVALID ? fallback : parsed;
    }
}
