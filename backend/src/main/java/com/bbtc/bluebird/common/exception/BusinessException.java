package com.bbtc.bluebird.common.exception;

import lombok.Getter;

/**
 * 业务异常，由 {@link GlobalExceptionHandler} 统一转 {@code ApiResult}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        super(code.getMsg());
        this.code = code;
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
