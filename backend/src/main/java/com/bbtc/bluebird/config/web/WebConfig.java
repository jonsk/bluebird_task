package com.bbtc.bluebird.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * 静态资源映射（02 §1.7.1，ADR-009）。
 *
 * <p>{@code spring.web.resources.add-mappings=false} 关闭默认映射后，必须显式注册，
 * 否则 {@code /assets/*.js|css}、{@code /favicon.ico} 因 Accept 不含 text/html 而 404 白屏。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

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
