package com.bbtc.bluebird.modules.config.application;

import java.util.List;
import java.util.Optional;

/**
 * 配置项注册表（唯一权威源）。
 *
 * <p>新增配置项只需在此登记：前端表单由 {@code GET /admin/config} 自动渲染，后端写入时按
 * 此表校验 key 与取值，未登记的 key 一律拒绝（防止配置表被塞入任意数据）。
 *
 * <p>{@code fallbackProperty} 指向 application.yml 中的等价属性：DB 无值时回落 yml，
 * 再回落 {@code defaultValue}，因此「不配置也能跑」。
 */
public final class ConfigDefinition {

    /**
     * @param type            STRING|INT|BOOL|SELECT|JSON
     * @param fallbackProperty application.yml 中的属性名（可空）
     */
    public record Item(
            String group,
            String key,
            String label,
            String type,
            String defaultValue,
            boolean secret,
            boolean requiresRestart,
            String description,
            List<String> options,
            String fallbackProperty) {
    }

    public record Group(String key, String label, String description, List<Item> items) {
    }

    private static final List<Group> GROUPS = List.of(
            new Group("casdoor", "Casdoor 对接", "统一身份源连接参数；client-secret 加密存储，读取时只返回掩码", List.of(
                    new Item("casdoor", "endpoint", "服务地址", "STRING", "",
                            false, false, "Casdoor 根地址，例如 https://casdoor.example.com", List.of(), null),
                    new Item("casdoor", "client-id", "Client ID", "STRING", "",
                            false, false, "Casdoor Application 的 Client ID", List.of(), null),
                    new Item("casdoor", "client-secret", "Client Secret", "STRING", "",
                            true, false, "AES-GCM 加密存储；留空表示不修改", List.of(), null),
                    new Item("casdoor", "organization", "组织名", "STRING", "",
                            false, false, "Casdoor Organization name（用户与组的 owner）", List.of(), null),
                    new Item("casdoor", "application-name", "应用名", "STRING", "",
                            false, false, "Casdoor Application name", List.of(), null),
                    new Item("casdoor", "redirect-uri", "回调地址", "STRING", "",
                            false, false, "必须与 Casdoor Application 中登记的一致，例如 http://host/api/v1/auth/oidc/callback",
                            List.of(), null),
                    new Item("casdoor", "scopes", "授权范围", "STRING", "openid profile email phone",
                            false, false, "空格分隔的 OIDC scope", List.of(), null),
                    new Item("casdoor", "role-map", "角色映射", "JSON",
                            "{\"bluebird-admin\":\"ADMIN\",\"bluebird-auditor\":\"AUDITOR\",\"bluebird-user-manager\":\"USER_MANAGER\",\"bluebird-common\":\"COMMON\"}",
                            false, false, "Casdoor 角色名 → 本地 RoleCode 的 JSON 映射；未命中回落 COMMON",
                            List.of(), null),
                    new Item("casdoor", "dept-source", "部门来源", "SELECT", "groups",
                            false, false, "从 Casdoor 的哪个维度同步部门", List.of("groups", "organizations"), null))),

            new Group("identity", "登录方式", "决定登录页展示与账密登录是否对普通用户开放", List.of(
                    new Item("identity", "provider", "认证方式", "SELECT", "LOCAL",
                            false, false, "LOCAL=仅本地账密；OIDC=Casdoor 单点登录（admin 账密仍保留）",
                            List.of("LOCAL", "OIDC"), "app.identity.provider"),
                    new Item("identity", "auto-create-user", "自动建用户", "BOOL", "true",
                            false, false, "Casdoor 用户首次登录时若本地不存在，是否自动创建", List.of(),
                            "app.identity.auto-create-user"))),

            new Group("orgsync", "组织同步", "Casdoor → 本地用户/部门/角色 的拉取策略", List.of(
                    new Item("orgsync", "mode", "同步模式", "SELECT", "NONE",
                            false, false, "PULL 才会启用定时拉取", List.of("NONE", "PULL", "PUSH", "SCIM"),
                            "app.orgsync.mode"),
                    new Item("orgsync", "adapter", "适配器", "SELECT", "casdoor",
                            false, false, "PULL 模式下使用的数据源适配器", List.of("casdoor"), "app.orgsync.adapter"),
                    new Item("orgsync", "cron", "同步时间（cron）", "STRING", "0 0 1 * * *",
                            false, false, "6 位 cron（秒 分 时 日 月 周），默认每日 01:00", List.of(),
                            "app.job.orgsync-cron"),
                    new Item("orgsync", "batch-size", "分页大小", "INT", "100",
                            false, false, "单次向 Casdoor 拉取的用户条数", List.of(), null))),

            new Group("security", "登录安全", "账密登录防暴破参数，修改后即时生效", List.of(
                    new Item("security", "login-max-fail", "最大失败次数", "INT", "5",
                            false, false, "连续失败达到该次数后锁定账号", List.of(), "app.security.login-max-fail"),
                    new Item("security", "login-lock-seconds", "锁定时长（秒）", "INT", "900",
                            false, false, "锁定持续时间", List.of(), "app.security.login-lock-seconds"))),

            new Group("audit", "审计", "日志留存策略", List.of(
                    new Item("audit", "retention-days", "日志保留天数", "INT", "180",
                            false, false, "操作/登录日志的保留天数（清理任务据此执行）", List.of(),
                            "app.audit.retention-days"))),

            new Group("storage", "文件存储", "上传限制；修改后需重启", List.of(
                    new Item("storage", "max-size", "单文件上限", "STRING", "50MB",
                            false, true, "例如 50MB / 2GB", List.of(), "app.storage.max-size"),
                    new Item("storage", "allowed-ext", "允许的扩展名", "STRING", "",
                            false, true, "逗号分隔，留空表示不限制", List.of(), "app.storage.allowed-ext"))));

    private ConfigDefinition() {
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static Optional<Group> group(String key) {
        return GROUPS.stream().filter(g -> g.key().equals(key)).findFirst();
    }

    public static Optional<Item> item(String group, String key) {
        return group(group).flatMap(g -> g.items().stream().filter(i -> i.key().equals(key)).findFirst());
    }
}
