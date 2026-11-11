package com.bbtc.bluebird;

import com.bbtc.bluebird.config.security.OrgSyncTokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织同步端点的鉴权失败必须用 HTTP 403 表达。
 *
 * <p>回归点：曾返回 HTTP 200 + 业务码 10003，对端（Casdoor / SCIM 客户端）与网关只看状态码，
 * 会把拒绝当成成功。
 */
class OrgSyncTokenFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 缺少令牌时返回403且不放行() throws Exception {
        MockHttpServletResponse res = invoke(new OrgSyncTokenFilter("secret", mapper), null);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("\"code\":" + 10003), res.getContentAsString());
        assertNull(lastChain.getRequest(), "鉴权失败不应进入后续链路");
    }

    @Test
    void 令牌错误时返回403且不放行() throws Exception {
        MockHttpServletResponse res = invoke(new OrgSyncTokenFilter("secret", mapper), "Bearer wrong");

        assertEquals(403, res.getStatus());
        assertNull(lastChain.getRequest());
    }

    @Test
    void 服务端未配置令牌时任何请求都返回403() throws Exception {
        MockHttpServletResponse res = invoke(new OrgSyncTokenFilter("", mapper), "Bearer ");

        assertEquals(403, res.getStatus());
        assertNull(lastChain.getRequest());
    }

    @Test
    void 令牌正确时放行() throws Exception {
        MockHttpServletResponse res = invoke(new OrgSyncTokenFilter("secret", mapper), "Bearer secret");

        assertEquals(200, res.getStatus());
        assertNotNull(lastChain.getRequest());
    }

    @Test
    void 非组织同步路径不参与校验() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new OrgSyncTokenFilter("secret", mapper).doFilter(req, res, chain);

        assertNotNull(chain.getRequest(), "其它接口不应被本过滤器拦截");
    }

    /** 供断言「是否放行」用的链路。 */
    private MockFilterChain lastChain;

    private MockHttpServletResponse invoke(OrgSyncTokenFilter filter, String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/org/push");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        lastChain = new MockFilterChain();
        filter.doFilter(req, res, lastChain);
        return res;
    }
}
