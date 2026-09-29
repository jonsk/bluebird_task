package com.bbtc.bluebird.modules.audit.aspect;

import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.IpUtils;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.audit.application.OperateLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Set;

/** 操作日志切面（02 §6.3）：记录 module/action/uri/参数脱敏/user/ip/ua/duration/结果。 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperateLogAspect {

    private static final Set<String> MASK = Set.of(
            "password", "pwd", "token", "access_token", "refresh_token", "secret", "oldpassword", "newpassword");

    private final OperateLogService operateLogService;

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, OperateLog annotation) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String msg = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            success = false;
            msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            throw t;
        } finally {
            try {
                HttpServletRequest req = currentRequest();
                operateLogService.record(new OperateLogService.Entry(
                        annotation.module(),
                        annotation.action(),
                        req == null ? null : req.getRequestURI(),
                        req == null ? null : req.getMethod(),
                        maskedParams(req),
                        UserContext.currentUserId(),
                        req == null ? null : IpUtils.clientIp(req),
                        req == null ? null : req.getHeader("User-Agent"),
                        System.currentTimeMillis() - start,
                        success,
                        msg));
            } catch (Exception ignored) {
                // 审计不得影响主流程
            }
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String maskedParams(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        req.getParameterMap().forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            String value = MASK.contains(k.toLowerCase(Locale.ROOT)) ? "***"
                    : (v != null && v.length > 0 ? v[0] : "");
            sb.append(k).append('=').append(value);
        });
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 便于测试的脱敏入口。 */
    public static String mask(String name, String value) {
        return MASK.contains(name.toLowerCase(Locale.ROOT)) ? "***" : value;
    }
}
