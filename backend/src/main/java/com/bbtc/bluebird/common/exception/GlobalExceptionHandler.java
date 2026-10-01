package com.bbtc.bluebird.common.exception;

import com.bbtc.bluebird.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
        // 业务异常自带的**具体原因**必须回传：否则用户只看到枚举兜底文案（如「参数校验失败」），
        // 不知道到底是「存在子部门」还是「文件格式不对」。异常里的文案本身就是面向用户的，
        // 不含堆栈/内部细节；未处理的 Throwable 仍只回 SYSTEM_ERROR，不泄漏内部信息。
        String message = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getCode().getMsg()
                : e.getMessage();
        return ApiResult.fail(e.getCode().getCode(), message);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
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
