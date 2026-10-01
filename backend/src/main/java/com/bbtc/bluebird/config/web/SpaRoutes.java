package com.bbtc.bluebird.config.web;

import java.util.List;

/**
 * SPA（history 路由）页面路径 —— **唯一事实来源**。
 *
 * <p>{@code SecurityConfig} 据此放行（浏览器直接访问/刷新深链时不带 Authorization 头，
 * 若不放行会拿到 {@code 10002 未登录} 的 JSON 而不是前端壳），
 * {@link SpaForwardController} 据此把请求转发到 {@code /index.html}。
 * 二者共用本清单，避免「加了页面却忘了放行/回退」导致刷新白屏或看到原始 JSON。
 *
 * <p>与 {@code frontend/src/router/routes.ts} 的 history 路由一一对应；
 * 不含 API（{@code /api/**}）与静态资源，也未包含前端通配兜底页（未知路径仍返回 JSON 404，
 * 以免把 API 的 404 契约吞掉）。
 */
public final class SpaRoutes {

    /** 页面路径模式（可由 {@code @RequestMapping} 与 Spring Security 的 requestMatchers 共用）。 */
    public static final String[] PAGE_PATTERNS = {
            "/",
            "/login",
            "/oauth/success",
            "/403",
            "/index",
            "/myWeek",
            "/myJoin",
            "/myDo",
            "/myCollect",
            "/allTask",
            "/calendar",
            "/calendar/*",
            "/admin/*",
    };

    /** 供测试遍历的固定页面（把 {@code /calendar/*} 之类的模式换成具体值）。 */
    public static final List<String> SAMPLE_PAGES = List.of(
            "/login", "/index", "/myWeek", "/myJoin", "/myDo", "/myCollect",
            "/allTask", "/calendar/2026-10-01", "/admin/org", "/admin/audit", "/403");

    private SpaRoutes() {
    }
}
