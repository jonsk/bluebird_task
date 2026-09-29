package com.bbtc.bluebird.common.filter;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 秘密不入 URL（02 §1.13.2）：query 参数名命中黑名单 → {@code 10001}。
 * 例外：{@code code} 仅在 {@code /api/v1/auth/**\/callback} 放行。
 */
@RequiredArgsConstructor
public class QuerySecretGuardFilter extends OncePerRequestFilter {

    private static final Set<String> BLACKLIST = Set.of(
            "token", "access_token", "refresh_token", "authorization",
            "password", "pwd", "secret", "client_secret");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean callbackPath = request.getRequestURI().matches("/api/v1/auth/.*/callback");
        for (String name : request.getParameterMap().keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (BLACKLIST.contains(lower)) {
                reject(response);
                return;
            }
            if ("code".equals(lower) && !callbackPath) {
                // code 仅回调放行；其余路径视为敏感
                reject(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResult.fail(ErrorCode.PARAM_ERROR));
    }
}
