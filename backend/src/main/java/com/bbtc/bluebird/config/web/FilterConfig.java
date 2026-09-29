package com.bbtc.bluebird.config.web;

import com.bbtc.bluebird.common.filter.QuerySecretGuardFilter;
import com.bbtc.bluebird.common.filter.ResponseCacheControlFilter;
import com.bbtc.bluebird.config.security.OrgSyncTokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.boot.autoconfigure.security.SecurityProperties;

/**
 * 过滤器注册（02 §1.13）。在 Spring Security 过滤链之后、业务之前。
 */
@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final ObjectMapper objectMapper;

    @Value("${app.storage.cache-max-age:300}")
    private long fileCacheMaxAge;

    @Value("${app.orgsync.push-token:}")
    private String orgSyncPushToken;

    @Bean
    public FilterRegistrationBean<OrgSyncTokenFilter> orgSyncTokenFilter() {
        var bean = new FilterRegistrationBean<>(new OrgSyncTokenFilter(orgSyncPushToken, objectMapper));
        bean.addUrlPatterns("/api/v1/org/push", "/api/v1/scim/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ResponseCacheControlFilter> responseCacheControlFilter() {
        var bean = new FilterRegistrationBean<>(new ResponseCacheControlFilter(fileCacheMaxAge));
        bean.addUrlPatterns("/api/v1/*");
        bean.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<QuerySecretGuardFilter> querySecretGuardFilter() {
        var bean = new FilterRegistrationBean<>(new QuerySecretGuardFilter(objectMapper));
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return bean;
    }
}
