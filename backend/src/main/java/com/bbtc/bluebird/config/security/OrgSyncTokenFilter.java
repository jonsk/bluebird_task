package com.bbtc.bluebird.config.security;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 组织同步 Bearer 鉴权（02 §3.3/§3.4）：{@code /org/push} 与 {@code /scim/**} 校验
 * {@code Authorization: Bearer <ORGSYNC_PUSH_TOKEN>}（常量时间比较）。
 */
@RequiredArgsConstructor
public class OrgSyncTokenFilter extends OncePerRequestFilter {

    private final String pushToken;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/org/push") || uri.startsWith("/api/v1/scim/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (!StringUtils.hasText(pushToken) || presented == null || !constantEquals(pushToken, presented)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResult.fail(ErrorCode.FORBIDDEN));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
