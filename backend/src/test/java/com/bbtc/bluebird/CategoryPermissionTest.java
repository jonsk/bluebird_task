package com.bbtc.bluebird;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分类写权限（ADR-015）——重点锁定普通用户（COMMON）能建自己的分类。
 *
 * <p>用户反馈：普通用户建不了个人分类与部门分类。实测个人分类本就可用（新建时 ownerId 为空，
 * 放行），**部门分类被挡住**：原判定是「创建者 / 部门负责人 / ADMIN」，而新建时创建者尚不存在，
 * 普通成员因此永远建不了部门分类。修正为：**本部门成员可为本部门新建**，改/删仍限创建者 /
 * 部门负责人 / ADMIN。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class CategoryPermissionTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-catperm-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode call(String token, String method, String url, String body) throws Exception {
        var req = method.equals("POST")
                ? post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                : get(url).header("Authorization", "Bearer " + token);
        if (body != null) {
            ((org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) req)
                    .content(body.getBytes(StandardCharsets.UTF_8));
        }
        String resp = mvc.perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp);
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    void commonUserCanCreateOwnPersonalAndDepartmentCategories() throws Exception {
        String admin = login("admin", "Test123456");

        // 根部门 + 一个子部门，并各放一个普通用户
        long root = call(admin, "GET", "/api/v1/departments", null).path("data").get(0).path("id").asLong();
        long child = call(admin, "POST", "/api/v1/departments",
                "{\"name\":\"分类权限-子部门\",\"parentId\":" + root + "}").path("data").asLong();

        String u1 = "catperm1" + System.currentTimeMillis() % 100000;
        String u2 = "catperm2" + System.currentTimeMillis() % 100000;
        call(admin, "POST", "/api/v1/users",
                "{\"username\":\"" + u1 + "\",\"name\":\"普通甲\",\"deptId\":" + root
                        + ",\"roleCode\":\"COMMON\",\"password\":\"Xx123!456\"}");
        call(admin, "POST", "/api/v1/users",
                "{\"username\":\"" + u2 + "\",\"name\":\"普通乙\",\"deptId\":" + child
                        + ",\"roleCode\":\"COMMON\",\"password\":\"Xx123!456\"}");

        String t1 = login(u1, "Xx123!456");

        // 1) 个人分类：本来就可以
        assertEquals(0, call(t1, "POST", "/api/v1/categories",
                "{\"name\":\"甲的私人分类\",\"scope\":\"PERSONAL\"}").path("code").asInt(), "普通用户应能建个人分类");

        // 2) 部门分类（本部门）：用户反馈的核心
        JsonNode ownDept = call(t1, "POST", "/api/v1/categories",
                "{\"name\":\"甲部门共享分类\",\"scope\":\"DEPARTMENT\",\"deptId\":" + root + "}");
        assertEquals(0, ownDept.path("code").asInt(), "普通用户应能为本部门建部门分类：" + ownDept);

        // 3) 不能替别的部门建
        assertEquals(10003, call(t1, "POST", "/api/v1/categories",
                "{\"name\":\"越权部门分类\",\"scope\":\"DEPARTMENT\",\"deptId\":" + child + "}").path("code").asInt(),
                "普通用户不应能替其他部门建部门分类");

        // 4) 不能建组织分类
        assertEquals(10003, call(t1, "POST", "/api/v1/categories",
                "{\"name\":\"越权组织分类\",\"scope\":\"ORG\"}").path("code").asInt(),
                "普通用户不应能建组织分类");

        // 5) 别的部门的人建自己部门的分类也应当可以
        String t2 = login(u2, "Xx123!456");
        assertEquals(0, call(t2, "POST", "/api/v1/categories",
                "{\"name\":\"乙部门共享分类\",\"scope\":\"DEPARTMENT\",\"deptId\":" + child + "}").path("code").asInt(),
                "子部门成员应能为本部门建部门分类");

        // 6) 可见性：甲只看到本部门 + 自己的个人分类，看不到乙部门的
        String tree = call(t1, "GET", "/api/v1/categories", null).toString();
        assertTrue(tree.contains("甲部门共享分类"), "应能看到本部门的部门分类：" + tree);
        assertTrue(tree.contains("甲的私人分类"), "应能看到自己的个人分类");
        assertTrue(!tree.contains("乙部门共享分类"), "不应看到其他部门的部门分类：" + tree);
    }
}
