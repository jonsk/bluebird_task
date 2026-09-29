package com.bbtc.bluebird.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 用户数据响应禁止缓存（02 §1.13.1）。
 *
 * <p>在响应提交前按路径写缓存头：{@code /api/v1/**} → {@code no-store}；
 * {@code GET /api/v1/files/**} → {@code private, max-age=<可配>}（禁 public，防共享代理缓存个人文件）。
 */
public class ResponseCacheControlFilter extends OncePerRequestFilter {

    private final long fileMaxAgeSeconds;

    public ResponseCacheControlFilter(long fileMaxAgeSeconds) {
        this.fileMaxAgeSeconds = fileMaxAgeSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/")) {
            if (uri.startsWith("/api/v1/files/") && "GET".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Cache-Control", "private, max-age=" + fileMaxAgeSeconds);
            } else {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
            }
        }
        chain.doFilter(request, response);
    }
}
