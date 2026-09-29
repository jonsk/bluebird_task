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
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 静态资源缺失（如浏览器自动请求的 {@code /favicon.ico}）→ 404，降级为 debug。
     *
     * <p>不归入 {@link #unhandled}：否则每次缺资源都会打印 ERROR 堆栈（启动日志噪声）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<Void> resourceNotFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return ApiResult.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Throwable.class)
    public ApiResult<Void> unhandled(Throwable e) {
        log.error("Unhandled error", e);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR);
    }
}
