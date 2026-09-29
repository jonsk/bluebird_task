package com.bbtc.bluebird.common.interceptor;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.filter.CachedBodyRequestWrapper;
import com.bbtc.bluebird.common.model.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防重复提交拦截器（02 §1.4 R8）。进程内实现（无 Redis，ADR-016）。
 */
@Component
public class RepeatSubmitInterceptor implements HandlerInterceptor {

    private record Mark(long expireAt) {
    }

    private final Map<String, Mark> marks = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RepeatSubmit rs = hm.getMethodAnnotation(RepeatSubmit.class);
        if (rs == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        purge(now);
        String key = buildKey(request);
        Mark existing = marks.get(key);
        if (existing != null && existing.expireAt() > now) {
            throw new BusinessException(ErrorCode.REPEAT_SUBMIT);
        }
        marks.put(key, new Mark(now + rs.seconds() * 1000L));
        return true;
    }

    private void purge(long now) {
        if (marks.size() < 512) {
            return;
        }
        marks.entrySet().removeIf(e -> e.getValue().expireAt() <= now);
    }

    private String buildKey(HttpServletRequest request) {
        Long uid = UserContext.currentUserId();
        String user = uid == null ? "anon" : uid.toString();
        StringBuilder sb = new StringBuilder(user).append('|')
                .append(request.getMethod()).append('|').append(request.getRequestURI());
        request.getParameterMap().forEach((k, v) -> {
            sb.append('|').append(k).append('=');
            if (v != null && v.length > 0) {
                sb.append(v[0]);
            }
        });
        // 含 JSON 请求体哈希：区分「同一接口、不同内容」的合法连发（如连续新建不同任务）
        CachedBodyRequestWrapper cached = unwrap(request);
        if (cached != null) {
            sb.append("|body=").append(sha256(new String(cached.getCachedBody(), StandardCharsets.UTF_8)));
        }
        return sha256(sb.toString());
    }

    /** 逐层解包 ServletRequestWrapper，找到缓存体包装（Spring Security 会再包一层）。 */
    private CachedBodyRequestWrapper unwrap(jakarta.servlet.ServletRequest request) {
        jakarta.servlet.ServletRequest current = request;
        while (current instanceof jakarta.servlet.ServletRequestWrapper wrapper) {
            if (current instanceof CachedBodyRequestWrapper cached) {
                return cached;
            }
            jakarta.servlet.ServletRequest next = wrapper.getRequest();
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current instanceof CachedBodyRequestWrapper cached ? cached : null;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
