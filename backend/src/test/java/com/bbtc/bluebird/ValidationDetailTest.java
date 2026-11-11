package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.GlobalExceptionHandler;
import com.bbtc.bluebird.modules.task.dto.TaskCreateReq;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数校验失败时的响应文案：字段级原因必须透传，且用中文。
 *
 * <p>回归点：{@code valid()} 曾把所有校验失败压成「参数校验失败」，前端无法告诉用户是哪一项不合规。
 */
class ValidationDetailTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 字段错误原文透传_去重且最多三条() {
        BindException e = new BindException(new Object(), "req");
        e.addError(new FieldError("req", "title", "任务标题不能为空"));
        e.addError(new FieldError("req", "title", "任务标题不能为空"));
        e.addError(new FieldError("req", "content", "任务内容不能超过 20000 个字符"));
        e.addError(new FieldError("req", "priority", "第三条"));
        e.addError(new FieldError("req", "parentId", "第四条"));

        var resp = handler.valid(e);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), resp.getBody().code());
        assertEquals("任务标题不能为空；任务内容不能超过 20000 个字符；第三条", resp.getBody().message());
    }

    @Test
    void 无字段信息的异常回落通用文案() {
        var resp = handler.valid(new IllegalArgumentException("boom"));

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), resp.getBody().code());
        assertEquals(ErrorCode.PARAM_ERROR.getMsg(), resp.getBody().message());
    }

    @Test
    void 约束注解自带中文文案() {
        TaskCreateReq blankTitle = new TaskCreateReq(
                "  ", "x".repeat(20001), null, null, null, null, null, null, null, null, null, null);

        Set<ConstraintViolation<TaskCreateReq>> violations = validator.validate(blankTitle);

        assertTrue(violations.stream().anyMatch(v -> "任务标题不能为空".equals(v.getMessage())));
        assertTrue(violations.stream().anyMatch(v -> "任务内容不能超过 20000 个字符".equals(v.getMessage())));
    }

    @Test
    void 合规任务请求无校验错误() {
        TaskCreateReq ok = new TaskCreateReq(
                "写周报", "今天写完了周报", null, 0L, null, null, "MEDIUM", null, null, null, null, null);

        assertTrue(validator.validate(ok).isEmpty());
    }
}
