package com.bbtc.bluebird.common.exception;

import com.bbtc.bluebird.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理（02 §1.5）。生产不向客户端暴露堆栈与内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> biz(BusinessException e) {
        return ApiResult.fail(e.getCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ApiResult<Void> valid(Exception e) {
        log.debug("参数校验失败: {}", e.getMessage());
        return ApiResult.fail(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> denied(AccessDeniedException e) {
        return ApiResult.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResult<Void> notFound(NoHandlerFoundException e) {
        return ApiResult.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Throwable.class)
    public ApiResult<Void> unhandled(Throwable e) {
        log.error("Unhandled error", e);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR);
    }
}
