package com.bbtc.bluebird.common.api;

import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.TraceContext;

/**
 * 统一响应体（02 §1.3）。HTTP 状态码统一 200，业务结果用 {@code code} 表达，{@code code==0} 为成功。
 */
public record ApiResult<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, TraceContext.get());
    }

    public static <T> ApiResult<T> fail(ErrorCode e) {
        return new ApiResult<>(e.getCode(), e.getMsg(), null, TraceContext.get());
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, TraceContext.get());
    }
}
