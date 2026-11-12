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

    /**
     * 页面路径模式（可由 {@code @RequestMapping} 与 Spring Security 的 requestMatchers 共用）。
     *
     * <p>对外只给副本（{@link #pagePatterns()}）：数组是可变对象，直接暴露 public static final
     * 数组等于把「唯一事实来源」交给任何调用方随意改写（SpotBugs MS_MUTABLE_ARRAY）。
     */
    private static final List<String> PAGE_PATTERN_LIST = List.of(
            "/",
            "/login",
            "/oauth/success",
            "/403",
            "/index",
            "/myJoin",
            "/myDo",
            "/myCollect",
            "/allTask",
            "/calendar",
            "/calendar/*",
            // 回收站（任务/日志统一入口，2026-10-30）
            "/recycle",
            // 工作日志（note）：列表/详情/日历/授权（探索、本部门、归档已下线）
            "/notes",
            "/notes/**",
            // 每周工作记录（一周一条，复用日志页面）
            "/weekly-notes",
            "/weekly-notes/**",
            // 后台：/admin 本身 + 全部子页（* 只匹配一层，故必须用 **）
            "/admin",
            "/admin/**");

    /** 页面路径模式副本：每次返回新数组，调用方改动不会污染这里的清单。 */
    public static String[] pagePatterns() {
        return PAGE_PATTERN_LIST.toArray(String[]::new);
    }

    /** 供测试遍历的固定页面（把 {@code /calendar/*} 之类的模式换成具体值）。 */
    public static final List<String> SAMPLE_PAGES = List.of(
            "/login", "/index", "/myJoin", "/myDo", "/myCollect",
            "/allTask", "/calendar/2026-10-01",
            "/recycle",
            "/notes", "/notes/123", "/notes/calendar", "/notes/subordinate",
            "/admin", "/admin/users", "/admin/casdoor", "/admin/notes", "/admin/logs", "/admin/settings",
            "/403");

    private SpaRoutes() {
    }
}
