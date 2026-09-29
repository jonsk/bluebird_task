package com.bbtc.bluebird.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 缓存 JSON 请求体（供防重提交按内容去重）。仅缓存 {@code application/json} 且体积有限者；
 * multipart 等一律跳过。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BodyCachingFilter extends OncePerRequestFilter {

    private static final long MAX_CACHE_BYTES = 512 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean cacheable = contentType != null && contentType.toLowerCase().contains("application/json");
        if (!cacheable) {
            chain.doFilter(request, response);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_CACHE_BYTES) {
            // 超大 JSON 不缓存（也无需以此防重）
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        chain.doFilter(new CachedBodyRequestWrapper(request, body), response);
    }

    static String bodyAsString(byte[] body) {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
