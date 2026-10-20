package com.bbtc.bluebird.modules.org.infrastructure.adapter;

import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.oidc.CasdoorClient;
import com.bbtc.bluebird.modules.org.dto.OrgDept;
import com.bbtc.bluebird.modules.org.dto.OrgPullData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Casdoor → 本地中间模型。
 *
 * <p>部门来源由配置 {@code casdoor.dept-source} 决定：{@code groups}（默认，Casdoor 的组树）
 * 或 {@code organizations}（组织，扁平）。
 * 角色按 {@code casdoor.role-map} 映射，未命中回落 {@code COMMON}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CasdoorPullAdapter implements OrgSyncAdapter {

    private final CasdoorClient casdoorClient;
    private final ConfigService config;
    private final ObjectMapper objectMapper;

    @Override
    public String source() {
        return "casdoor";
    }

    @Override
    public OrgPullData pull() {
        String deptSource = config.get("casdoor", "dept-source", "groups");
        List<OrgDept> depts = "organizations".equalsIgnoreCase(deptSource)
                ? organizations() : groups();
        List<Map<String, Object>> rawUsers = casdoorClient.getAllUsers();
        List<OrgDept.OrgUser> users = new ArrayList<>(rawUsers.size());
        for (Map<String, Object> u : rawUsers) {
            users.add(toUser(u, deptSource));
        }
        log.info("Casdoor 拉取完成：部门 {} 个，用户 {} 个", depts.size(), users.size());
        return new OrgPullData("CASDOOR", depts, users);
    }

    // ==================== 部门 ====================

    private List<OrgDept> groups() {
        List<Map<String, Object>> raw = casdoorClient.getGroups();
        List<OrgDept> out = new ArrayList<>(raw.size());
        int i = 0;
        for (Map<String, Object> g : raw) {
            String name = str(g.get("name"));
            if (name == null) {
                continue;
            }
            out.add(new OrgDept(name, str(g.get("parentId")),
                    firstNonBlank(str(g.get("displayName")), name), i++));
        }
        return out;
    }

    private List<OrgDept> organizations() {
        List<Map<String, Object>> raw = casdoorClient.getOrganizations();
        List<OrgDept> out = new ArrayList<>(raw.size());
        int i = 0;
        for (Map<String, Object> o : raw) {
            String name = str(o.get("name"));
            if (name == null) {
                continue;
            }
            out.add(new OrgDept(name, null,
                    firstNonBlank(str(o.get("displayName")), name), i++));
        }
        return out;
    }

    // ==================== 用户 ====================

    /**
     * @param deptSource {@code groups} 时用用户的 groups 作为部门；{@code organizations} 时用其 owner
     *                   （Casdoor 用户归属的组织名）——否则「部门来源=organizations」会拉出部门却关联不上人
     */
    private OrgDept.OrgUser toUser(Map<String, Object> u, String deptSource) {
        String externalId = firstNonBlank(str(u.get("sub")), str(u.get("id")), str(u.get("name")));
        String username = firstNonBlank(str(u.get("name")), str(u.get("preferred_username")), externalId);
        String displayName = firstNonBlank(str(u.get("displayName")), username);
        String status = str(u.get("deletedTime")) == null ? "ACTIVE" : "DISABLED";
        List<String> roles = stringList(u.get("roles"));

        List<String> deptExternalIds;
        if ("organizations".equalsIgnoreCase(deptSource)) {
            String owner = str(u.get("owner"));
            deptExternalIds = owner == null ? List.of() : List.of(bareName(owner));
        } else {
            // Casdoor 的 user.groups 可能带 owner 前缀（如 "bluebird/dev"），组本身 name 不带
            deptExternalIds = stringList(u.get("groups")).stream().map(this::bareName).toList();
        }

        return new OrgDept.OrgUser(
                externalId,
                username,
                displayName,
                firstNonBlank(str(u.get("phone")), str(u.get("phone_number"))),
                str(u.get("email")),
                firstNonBlank(str(u.get("avatar")), str(u.get("picture"))),
                status,
                mapRole(roles),
                deptExternalIds,
                deptExternalIds.isEmpty() ? null : deptExternalIds.get(0));
    }

    /** 去掉 Casdoor 的 `owner/` 前缀，只保留实体名，便于与 get-groups 返回的 name 对齐。 */
    private String bareName(String value) {
        int slash = value.lastIndexOf('/');
        return slash >= 0 && slash < value.length() - 1 ? value.substring(slash + 1) : value;
    }

    private String mapRole(List<String> roleNames) {
        if (roleNames.isEmpty()) {
            return "COMMON";
        }
        JsonNode map = roleMap();
        for (String name : roleNames) {
            JsonNode v = map.get(name);
            if (v != null && !v.isNull()) {
                return v.asText().toUpperCase(Locale.ROOT);
            }
        }
        return "COMMON";
    }

    private JsonNode roleMap() {
        String json = config.get("casdoor", "role-map", "{}");
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            return node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("role-map 不是合法 JSON，按空映射处理：{}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(o -> {
            if (o instanceof String s) {
                return s;
            }
            if (o instanceof Map<?, ?> m) {
                Object n = m.get("name");
                return n == null ? null : String.valueOf(n);
            }
            return null;
        }).filter(s -> s != null && !s.isBlank()).toList();
    }

    private String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
