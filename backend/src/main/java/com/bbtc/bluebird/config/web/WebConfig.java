package com.bbtc.bluebird.config.web;

import com.bbtc.bluebird.common.interceptor.RepeatSubmitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * 静态资源映射（02 §1.7.1，ADR-009）+ 防重复提交拦截器（02 §1.4 R8）。
 *
 * <p>{@code spring.web.resources.add-mappings=false} 关闭默认映射后，必须显式注册，
 * 否则 {@code /assets/*.js|css}、{@code /favicon.ico} 因 Accept 不含 text/html 而 404 白屏。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RepeatSubmitInterceptor repeatSubmitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA history 路由回退（ADR-009，02 §1.7.1）：把页面路径转发到内嵌的 index.html。
        // 用运行时注册而非 @RequestMapping 注解——注解元素要求编译期常量，无法引用 SpaRoutes 数组，
        // 于是「页面清单」会分裂成两份；这里与 SecurityConfig 共用同一份 SpaRoutes。
        for (String page : SpaRoutes.PAGE_PATTERNS) {
            registry.addViewController(page).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
        registry.addResourceHandler("/")
                .addResourceLocations("classpath:/static/index.html")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/index.html")
                .setCacheControl(CacheControl.noCache());
    }
}
