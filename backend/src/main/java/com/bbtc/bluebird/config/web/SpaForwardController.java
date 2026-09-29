package com.bbtc.bluebird.config.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA history 路由回退（ADR-009，02 §1.7.1）。
 *
 * <p>非 {@code /api/v1/**}、{@code /actuator/**}、{@code /v3/api-docs/**}、{@code /swagger-ui/**} 的
 * HTML 深层路由转发 {@code /index.html}；API 的 404 仍由 NoHandlerFoundException 转 JSON，不被吞掉。
 * 桩实现：M1 阶段前端尚未内嵌，仅声明回退入口。
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {"/", "/login", "/oauth/success", "/tasks", "/calendar", "/admin/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
