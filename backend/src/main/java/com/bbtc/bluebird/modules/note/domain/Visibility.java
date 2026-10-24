package com.bbtc.bluebird.modules.note.domain;

import java.util.Locale;

/** 日志可见性。Service 层校验（SQL 不加 CHECK，与 task 表一致）。 */
public enum Visibility {
    PRIVATE,
    INTERNAL,
    PUBLIC;

    /** 解析枚举（大小写敏感，与前端契约一致）；非法返回 {@code null}。 */
    public static Visibility of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
