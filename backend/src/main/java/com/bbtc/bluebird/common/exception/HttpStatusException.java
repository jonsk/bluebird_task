package com.bbtc.bluebird.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 协议级异常：需要返回真实 HTTP 状态码（403/404/400/409），而非「HTTP 200 + 业务码」。
 *
 * <p>业务异常（{@link BusinessException}）继续走 {@code ApiResult.fail} + HTTP 200；
 * 本异常只用于 note/task 附件等**需要协议级状态**的端点（前端按 {@code err.status} 分支渲染
 * 404/403 空态，见《Note 模块后端开发方案》§11）。
 */
@Getter
public class HttpStatusException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public HttpStatusException(HttpStatus status) {
        this(status, null, null);
    }

    public HttpStatusException(HttpStatus status, ErrorCode code) {
        this(status, code, null);
    }

    public HttpStatusException(HttpStatus status, ErrorCode code, String message) {
        super(message != null ? message : (code != null ? code.getMsg() : status.getReasonPhrase()));
        this.status = status;
        this.code = code;
    }

    public static HttpStatusException notFound() {
        return new HttpStatusException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    public static HttpStatusException forbidden() {
        return new HttpStatusException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    public static HttpStatusException badRequest(String message) {
        return new HttpStatusException(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_ERROR, message);
    }
}
