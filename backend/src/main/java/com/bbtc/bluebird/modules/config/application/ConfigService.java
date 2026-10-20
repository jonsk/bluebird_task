package com.bbtc.bluebird.modules.config.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.domain.SysConfig;
import com.bbtc.bluebird.modules.config.dto.ConfigGroupVO;
import com.bbtc.bluebird.modules.config.dto.ConfigItemVO;
import com.bbtc.bluebird.modules.config.infrastructure.SysConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 配置读写（sys_config）。
 *
 * <p>取值优先级：**DB > application.yml > 代码默认值**。密文列读取时自动解密，
 * 对外（{@link #list()}）只返回掩码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    /** 密文掩码：前端回传该值表示「不修改」。 */
    public static final String MASK = "******";

    private final SysConfigMapper mapper;
    private final ConfigCrypto crypto;
    private final Environment env;
    private final ObjectMapper objectMapper;

    // ==================== 读取 ====================

    /** 取生效值（DB > yml > 默认）；密文自动解密。 */
    public String get(String group, String key) {
        Optional<ConfigDefinition.Item> def = ConfigDefinition.item(group, key);
        SysConfig row = row(group, key);
        if (row != null && row.getConfigValue() != null) {
            boolean encrypted = row.getEncrypted() != null && row.getEncrypted() == 1;
            return encrypted ? crypto.decrypt(row.getConfigValue()) : row.getConfigValue();
        }
        if (def.isPresent()) {
            String prop = def.get().fallbackProperty();
            if (prop != null) {
                String v = env.getProperty(prop);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return def.get().defaultValue();
        }
        return null;
    }

    public String get(String group, String key, String def) {
        String v = get(group, key);
        return v == null || v.isBlank() ? def : v;
    }

    public int getInt(String group, String key, int def) {
        try {
            return Integer.parseInt(get(group, key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String group, String key, boolean def) {
        String v = get(group, key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    /** 认证方式：LOCAL | OIDC。 */
    public String provider() {
        return get("identity", "provider", "LOCAL").trim().toUpperCase(Locale.ROOT);
    }

    public boolean autoCreateUser() {
        return getBool("identity", "auto-create-user", true);
    }

    /** 组织同步模式：NONE | PULL | PUSH | SCIM。 */
    public String orgSyncMode() {
        return get("orgsync", "mode", "NONE").trim().toUpperCase(Locale.ROOT);
    }

    public String orgSyncAdapter() {
        return get("orgsync", "adapter", "").trim().toLowerCase(Locale.ROOT);
    }

    /** 取整组生效值（解密后），供内部组件使用。 */
    public Map<String, String> values(String group) {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigDefinition.group(group).ifPresent(g -> g.items()
                .forEach(i -> out.put(i.key(), get(group, i.key()))));
        return out;
    }

    // ==================== 展示 ====================

    public List<ConfigGroupVO> list() {
        List<ConfigGroupVO> out = new ArrayList<>();
        for (ConfigDefinition.Group g : ConfigDefinition.groups()) {
            out.add(toVO(g));
        }
        return out;
    }

    public ConfigGroupVO group(String group) {
        ConfigDefinition.Group g = ConfigDefinition.group(group)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "配置分组不存在：" + group));
        return toVO(g);
    }

    private ConfigGroupVO toVO(ConfigDefinition.Group g) {
        List<ConfigItemVO> items = new ArrayList<>();
        for (ConfigDefinition.Item def : g.items()) {
            SysConfig row = row(g.key(), def.key());
            boolean hasDbValue = row != null && row.getConfigValue() != null;
            String source;
            String value;
            if (hasDbValue) {
                source = "DB";
                value = def.secret() ? MASK : (row.getEncrypted() != null && row.getEncrypted() == 1
                        ? crypto.decrypt(row.getConfigValue()) : row.getConfigValue());
            } else {
                String fromYml = def.fallbackProperty() == null ? null : env.getProperty(def.fallbackProperty());
                source = fromYml != null && !fromYml.isBlank() ? "YML" : "DEFAULT";
                value = def.secret() ? "" : get(g.key(), def.key());
            }
            items.add(new ConfigItemVO(def.key(), def.label(), value, def.type(),
                    def.secret(), def.secret() && hasDbValue, source, def.description(),
                    def.options(), def.requiresRestart()));
        }
        return new ConfigGroupVO(g.key(), g.label(), g.description(), items);
    }

    // ==================== 写入 ====================

    @Transactional
    public ConfigGroupVO save(String group, Map<String, String> values, Long userId) {
        ConfigDefinition.Group g = ConfigDefinition.group(group)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "配置分组不存在：" + group));
        if (values == null || values.isEmpty()) {
            return toVO(g);
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            ConfigDefinition.Item def = ConfigDefinition.item(group, e.getKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "未知配置项：" + e.getKey()));
            String v = e.getValue();
            // null / 掩码 = 不修改；空串 = 清空（回落 yml/默认）
            if (v == null || MASK.equals(v)) {
                continue;
            }
            v = v.trim();
            String stored = v.isEmpty() ? null : v;
            validate(def, stored);

            SysConfig row = row(group, def.key());
            boolean isNew = row == null;
            if (isNew) {
                row = new SysConfig();
                row.setGroupKey(group);
                row.setConfigKey(def.key());
            }
            row.setValueType(def.type());
            row.setEncrypted(def.secret() ? 1 : 0);
            row.setConfigValue(stored == null ? null : (def.secret() ? crypto.encrypt(stored) : stored));
            row.setDescription(def.description());
            row.setUpdatedBy(userId);
            row.setUpdatedAt(Instant.now());
            if (isNew) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        }
        log.info("配置已更新：group={} by user={}", group, userId);
        return toVO(g);
    }

    private void validate(ConfigDefinition.Item def, String value) {
        if (value == null) {
            return;
        }
        switch (def.type()) {
            case "INT" -> {
                try {
                    int n = Integer.parseInt(value);
                    if (n < 0) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR, def.label() + "不能为负数");
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, def.label() + "必须是整数");
                }
            }
            case "BOOL" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, def.label() + "必须是 true/false");
                }
            }
            case "SELECT" -> {
                if (!def.options().contains(value)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            def.label() + "取值必须是：" + String.join("/", def.options()));
                }
            }
            case "JSON" -> {
                try {
                    objectMapper.readTree(value);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, def.label() + "必须是合法 JSON");
                }
            }
            default -> {
                // STRING：不校验
            }
        }
    }

    private SysConfig row(String group, String key) {
        return mapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getGroupKey, group)
                .eq(SysConfig::getConfigKey, key)
                .last("limit 1"));
    }

    /** 内部诊断用：某组中已落库的条目数。 */
    public Map<String, Long> countByGroup() {
        Map<String, Long> out = new HashMap<>();
        for (ConfigDefinition.Group g : ConfigDefinition.groups()) {
            Long c = mapper.selectCount(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getGroupKey, g.key()));
            out.put(g.key(), c == null ? 0L : c);
        }
        return out;
    }
}
