package com.bbtc.bluebird.common.exception;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.model.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理（02 §1.5）。生产不向客户端暴露堆栈与内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 提示里最多带几条字段错误：再多用户也读不完，其余由前端表单自己标红。 */
    private static final int MAX_DETAIL_ITEMS = 3;

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

    @ExceptionHandler(HttpStatusException.class)
    public ResponseEntity<ApiResult<Void>> httpStatus(HttpStatusException e) {
        ErrorCode code = e.getCode() != null ? e.getCode() : ErrorCode.SYSTEM_ERROR;
        ApiResult<Void> body = new ApiResult<>(code.getCode(), e.getMessage(), null, TraceContext.get());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> valid(Exception e) {
        log.debug("参数校验失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.fail(ErrorCode.PARAM_ERROR.getCode(), detail(e)));
    }

    /**
     * 字段级校验文案透传：不这样做，用户只能看到「参数校验失败」，不知道是哪一项不合规。
     *
     * <p>约束注解一律自带中文 {@code message}（枚举兜底文案仍是 PARAM_ERROR 的 msg），
     * 多条错误按「；」合并并去重，最多 3 条，避免把整个 DTO 的错误一次性糊到提示框里。
     */
    private String detail(Exception e) {
        List<FieldError> errors = switch (e) {
            case MethodArgumentNotValidException m -> m.getBindingResult().getFieldErrors();
            case BindException b -> b.getBindingResult().getFieldErrors();
            default -> List.of();
        };
        if (!errors.isEmpty()) {
            String joined = errors.stream()
                    .map(FieldError::getDefaultMessage)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(MAX_DETAIL_ITEMS)
                    .collect(Collectors.joining("；"));
            if (StringUtils.hasText(joined)) {
                return joined;
            }
        }
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return "参数「" + mismatch.getName() + "」类型不正确";
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体不是合法的 JSON，或字段类型不匹配";
        }
        return ErrorCode.PARAM_ERROR.getMsg();
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
