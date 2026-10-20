package com.bbtc.bluebird.modules.identity.oidc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Casdoor 用户 → 本地 {@code sys_user} 的即时映射（登录回调时调用）。
 *
 * <p>与定时全量同步的关系：本类保证「首次登录即可用」，定时任务负责补齐其余人员与部门。
 * 本地 admin（{@code external_id} 为空、有密码）不会被 Casdoor 账号接管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasdoorUserService {

    private static final String SOURCE = "CASDOOR";

    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final ConfigService config;
    private final ObjectMapper objectMapper;

    @Transactional
    public SysUser upsert(Map<String, Object> info) {
        String externalId = firstNonBlank(str(info.get("sub")), str(info.get("id")), str(info.get("name")));
        if (externalId == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_LOGIN_FAILED, "Casdoor 用户缺少唯一标识");
        }
        String username = firstNonBlank(str(info.get("name")), str(info.get("preferred_username")), externalId);
        String displayName = firstNonBlank(str(info.get("displayName")), str(info.get("name")), username);
        String mobile = firstNonBlank(str(info.get("phone")), str(info.get("phone_number")));
        String email = str(info.get("email"));
        String avatar = firstNonBlank(str(info.get("avatar")), str(info.get("picture")));
        String roleCode = mapRole(info.get("roles"));
        List<String> groups = stringList(info.get("groups"));
        Long deptId = findDeptId(groups);

        SysUser user = findByExternalId(externalId);
        if (user == null) {
            SysUser sameName = findByUsername(username);
            if (sameName != null && sameName.getExternalId() == null) {
                // 本地账号（admin）不允许被同名 Casdoor 账号接管
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "用户名「" + username + "」已被本地账号占用，请在 Casdoor 侧调整用户名");
            }
            if (sameName != null) {
                username = username + "-" + tail(externalId);
            }
            if (!config.autoCreateUser()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION_EXTERNAL,
                        "本地尚未建立该用户，且未开启自动创建");
            }
            user = new SysUser();
            user.setExternalId(externalId);
            user.setExternalSource(SOURCE);
            user.setUsername(username);
            user.setName(displayName);
            user.setMobile(mobile);
            user.setEmail(email);
            user.setAvatarUrl(avatar);
            user.setDeptId(deptId);
            user.setRoleCode(roleCode);
            user.setStatus("ACTIVE");
            user.setMustChangePassword(0);
            userMapper.insert(user);
            log.info("Casdoor 用户首次登录已创建：{} ({})", username, externalId);
        } else {
            user.setName(displayName);
            user.setMobile(mobile);
            user.setEmail(email);
            user.setAvatarUrl(avatar);
            user.setExternalSource(SOURCE);
            if (deptId != null) {
                user.setDeptId(deptId);
            }
            if (roleCode != null) {
                user.setRoleCode(roleCode);
            }
            user.setStatus("ACTIVE");
            userMapper.updateById(user);
        }
        linkDepartments(user.getId(), groups);
        return user;
    }

    private SysUser findByExternalId(String externalId) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getExternalId, externalId)
                .last("limit 1"));
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    private Long findDeptId(List<String> groups) {
        for (String g : groups) {
            Department d = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                    .eq(Department::getCorpId, g)
                    .last("limit 1"));
            if (d != null) {
                return d.getId();
            }
        }
        return null;
    }

    private void linkDepartments(Long userId, List<String> groups) {
        if (userId == null || groups.isEmpty()) {
            return;
        }
        for (String g : groups) {
            Department d = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                    .eq(Department::getCorpId, g)
                    .last("limit 1"));
            if (d == null) {
                continue;
            }
            Long cnt = userDepartmentMapper.selectCount(Wrappers.<UserDepartment>lambdaQuery()
                    .eq(UserDepartment::getUserId, userId)
                    .eq(UserDepartment::getDeptId, d.getId()));
            if (cnt != null && cnt > 0) {
                continue;
            }
            UserDepartment ud = new UserDepartment();
            ud.setUserId(userId);
            ud.setDeptId(d.getId());
            ud.setIsPrimary(1);
            userDepartmentMapper.insert(ud);
        }
    }

    /** Casdoor roles[] → 本地 RoleCode；未命中回落 COMMON。 */
    @SuppressWarnings("unchecked")
    private String mapRole(Object rolesRaw) {
        List<String> names = stringList(rolesRaw);
        if (names.isEmpty()) {
            return "COMMON";
        }
        JsonNode map = roleMap();
        for (String name : names) {
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
            log.warn("role-map 配置不是合法 JSON，按空映射处理：{}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /** Casdoor 的 groups/roles 既可能是字符串数组，也可能是对象数组（取 name）。 */
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
        return s.isEmpty() ? null : s;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String tail(String s) {
        return s.length() <= 6 ? s : s.substring(s.length() - 6);
    }
}
