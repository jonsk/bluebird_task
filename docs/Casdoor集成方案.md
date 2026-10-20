# BlueBird 接入 Casdoor —— 实施记录

> 文档类型：**已实施**（as-built）技术记录
> 适用版本：Spring Boot 3.3.5 + Java 21 + SQLite + Vue3 + TS（单制品 jar，ADR-009）
> 覆盖范围：OIDC 登录、组织/用户/角色同步、配置中心、后台管理界面、审计日志
> 本文与代码同步维护；与早期「评估版方案」的差异见第九节

---

## 一、目标与交付范围

把 Casdoor 作为外部权威身份源接入 BlueBird，同时保留自有 JWT 会话与本地 RBAC：

| 能力 | 交付状态 |
|---|---|
| 普通用户经 Casdoor（OIDC 授权码流）登录 | ✅ 已实现 |
| 定时/手动从 Casdoor 拉取组织、用户、角色 | ✅ 已实现 |
| Casdoor 连接参数在**数据库**中配置（不入 yml、不入 git） | ✅ 已实现 |
| `/admin` 独立后台（用户机构 / Casdoor 配置 / 日志 / 常规设置） | ✅ 已实现 |
| 登录、退出、组织同步的审计日志 | ✅ 已实现 |
| `admin` 本地账密兜底入口 | ✅ 保留（provider=OIDC 时仅 admin 可用账密） |

---

## 二、总体架构

```
                         ┌──────────────── Casdoor (IdP) ────────────────┐
                         │  /login/oauth/authorize   /api/userinfo        │
                         │  /api/get-users  /api/get-groups  …            │
                         └───────▲───────────────────────────▲───────────┘
              OIDC 授权码流      │                           │  REST（clientId+Secret）
┌────────────────────────────────┴───────────────────────────┴──────────────────────────┐
│ BlueBird 后端                                                                          │
│                                                                                        │
│  identity/oidc                        org/                                             │
│  ├ OidcLoginController  /auth/oidc/*   ├ OrgSyncService  (mode=PULL 分发)               │
│  ├ CasdoorClient        (HTTP)         ├ OrgSyncApplier  (@Transactional 落库)          │
│  └ CasdoorUserService   (upsert)       ├ adapter/OrgSyncAdapter (接口)                  │
│                                        └ adapter/CasdoorPullAdapter (实现)              │
│                                                                                        │
│  config/                                                                               │
│  ├ ConfigDefinition (配置注册表·唯一权威源)                                             │
│  ├ ConfigService    (DB > yml > 默认)                                                   │
│  ├ ConfigCrypto     (AES-256-GCM)                                                       │
│  └ AdminConfigController  /admin/config/**                                              │
│                                                                                        │
│  sys_config   org_sync_log   audit_login_log   sys_user(+external_source)               │
└────────────────────────────────────────────────────────────────────────────────────────┘
                         ▲ 同源单制品（前端 dist 内嵌 jar）
                  ┌──────┴───────┐
                  │ Vue3 前端     │
                  │ /admin 独立布局│
                  └───────────────┘
```

**关键设计取舍**：登录（验身份）与同步（补人员/组织）**解耦**——SSO 回调只负责把当前用户 upsert 成本地账号并签发 JWT；人员增减、部门调整、角色变化靠 PULL 定时全量对齐。

---

## 三、数据库（V5__casdoor_integration.sql）

```sql
-- 配置中心（KV，分组 + 类型 + 密文标记）
CREATE TABLE sys_config (
  id INTEGER PRIMARY KEY, group_key TEXT NOT NULL, config_key TEXT NOT NULL,
  config_value TEXT, value_type TEXT NOT NULL DEFAULT 'STRING',
  encrypted INTEGER NOT NULL DEFAULT 0, description TEXT,
  updated_by INTEGER, updated_at TEXT, UNIQUE (group_key, config_key));

-- 组织同步历史（内存态 OrgSyncState 只留最近一次，历史落表）
CREATE TABLE org_sync_log (
  id INTEGER PRIMARY KEY, source TEXT NOT NULL, sync_type TEXT NOT NULL,
  status TEXT NOT NULL, started_at TEXT NOT NULL, finished_at TEXT,
  total INTEGER, inserted INTEGER, updated INTEGER, skipped INTEGER, error_message TEXT);

-- 登录日志扩展：来源 / 事件 / 外部 id
ALTER TABLE audit_login_log ADD COLUMN provider TEXT;
ALTER TABLE audit_login_log ADD COLUMN event_type TEXT DEFAULT 'LOGIN';
ALTER TABLE audit_login_log ADD COLUMN external_id TEXT;

-- 来源标记，避免未来多身份源混淆
ALTER TABLE sys_user ADD COLUMN external_source TEXT;
ALTER TABLE sys_department ADD COLUMN external_source TEXT;
```

> `externalId`（用户）与 `corpId`（部门）在 V1 已存在，V5 只补「来源标记」。
> 登录日志的 `provider`/`event_type` 对历史行为 NULL，读取侧按 `LOCAL` / `LOGIN` 兼容。

---

## 四、配置中心

### 4.1 分层与优先级

```
DB(sys_config)  >  application.yml  >  ConfigDefinition.defaultValue
```

`ConfigDefinition` 是**唯一权威源**：新增配置项只改这一处，后台表单自动渲染、后端写入自动校验（未登记的 key 一律拒绝，防止配置表被塞任意数据）。

### 4.2 配置项清单

| 分组 | key | 类型 | 说明 |
|---|---|---|---|
| `casdoor` | `endpoint` | STRING | Casdoor 根地址 |
| | `client-id` / `client-secret` | STRING / **密文** | Application 凭据 |
| | `organization` | STRING | Organization name（用户与组的 owner） |
| | `application-name` | STRING | Application name |
| | `redirect-uri` | STRING | 须与 Casdoor 登记一致；留空则按请求头推导 |
| | `scopes` | STRING | 默认 `openid profile email phone` |
| | `role-map` | JSON | Casdoor 角色名 → 本地 RoleCode |
| | `dept-source` | SELECT | `groups` \| `organizations` |
| `identity` | `provider` | SELECT | `LOCAL` \| `OIDC`（回退 `app.identity.provider`） |
| | `auto-create-user` | BOOL | 首登是否自动建本地账号 |
| `orgsync` | `mode` | SELECT | `NONE` \| `PULL` \| `PUSH` \| `SCIM` |
| | `adapter` | SELECT | `casdoor` |
| | `cron` | STRING | 6 位 cron，回退 `app.job.orgsync-cron` |
| | `batch-size` | INT | 单页拉取条数（1..500） |
| `security` | `login-max-fail` / `login-lock-seconds` | INT | 防暴破，**改后即时生效** |
| `audit` | `retention-days` | INT | 日志留存天数 |
| `storage` | `max-size` / `allowed-ext` | STRING | 需重启 |

### 4.3 密文处理

- 算法：**AES-256-GCM**，随机 IV 12 字节，TAG 128 位，密文 `Base64(iv || ciphertext+tag)`。
- 主密钥来源（按序）：环境变量 `BB_CONFIG_SECRET` → 密钥文件 `./data/.config-key`（缺失则自动生成并落盘，权限尽力收紧）。
- 读取：**永不返回明文**。已配置返回掩码 `******` 且 `hasSecret=true`；未配置返回空串。
- 写入：值为 `null` 或 `******` → 不修改；空串 → 清空（回落 yml/默认）。
- 解密失败不抛异常，返回 `null` 并按「未配置」处理——避免一条坏数据导致整个后台页不可用。

### 4.4 运行期生效

| 配置 | 生效时机 |
|---|---|
| `identity.provider` | 立即（每次 `/auth/login`、`/auth/external/config` 实时读） |
| `orgsync.mode` / `adapter` | 立即（`OrgSyncJob` 每次执行前读；手动同步读） |
| `security.*` | 立即（`LoginAttemptState` 实时读） |
| `orgsync.cron` | **需重启**（`@Scheduled` 注解在启动期解析） |
| `storage.*` | **需重启**（标记 `requiresRestart`，UI 会提示） |

---

## 五、OIDC 登录链路

### 5.1 端点

| 端点 | 说明 |
|---|---|
| `GET /api/v1/auth/oidc/redirect?redirect=/index` | 生成随机 `state`，写 httpOnly cookie `bb_oidc_state`（`state.Base64(target)`，10 分钟，Lax），302 到 Casdoor 授权页 |
| `GET /api/v1/auth/oidc/callback?code=&state=` | 清 cookie → 校验 state → 换 token → 拉 userinfo → 本地 upsert → 签发自有 JWT → 302 到 `/oauth/success#access_token=..&refresh_token=..&redirect=..` |
| `GET /api/v1/auth/external/config` | `{provider}`；OIDC 时追加 `authorizeUrl` / `displayName` / `logoutUrl` |
| `POST /api/v1/auth/logout` | 吊销 refresh token + 记 `LOGOUT` 事件；前端随后整页跳上游登出 |

**为什么 token 走 hash fragment**：`#` 之后的内容不会进入服务端访问日志、也不会随 `Referer` 外泄；`OidcSuccessView` 读取后立即 `history.replaceState` 清掉。

**state 为什么放 cookie**：`SameSite=Lax` 的 httpOnly cookie 天然防 CSRF，且不需要服务端会话（单实例 SQLite 部署无共享存储）。

### 5.2 本地账号映射（`CasdoorUserService.upsert`）

1. 取外部 id：`sub` → `id` → `name`（依次回退）。
2. 按 `externalId` 查本地用户；命中则更新 `name/mobile/email/avatarUrl`。
3. 未命中：
   - `auto-create-user=false` → 抛 `20008`（拒绝首次登录）；
   - 否则新建，`externalSource='casdoor'`，`password` 留空。
4. **本地 admin 保护**：若同名本地账号 `externalId == null` 且设有密码，**不允许被 Casdoor 账号接管**，抛 `20006`。防止外部身份源覆盖应急管理员。
5. 角色：`role-map` 映射 Casdoor 角色名 → `RoleCode`，未命中回落 `COMMON`。
6. 部门：按 `groups` 匹配 `sys_department.corp_id` 写入 `sys_user_department`。
7. 状态：`deletedTime` 非空 → `DISABLED`；登录时若 `status != ACTIVE` 直接拒绝并记失败日志。

### 5.3 账密登录的限制

`provider=OIDC` 时，`/auth/login` 仅放行内置 admin（`app.bootstrap.admin-username`），其他账号返回 `20007 当前已启用统一认证，请使用单点登录`。该分支**同样记录失败日志**，便于发现「有人还在试账密」。

---

## 六、组织同步

### 6.1 分层

```
OrgSyncJob (@Scheduled, mode=PULL)  ─┐
OrgSyncController POST /org/sync    ─┴→ OrgSyncService.sync()
                                          ├ resolveAdapter() → OrgSyncAdapter.pull() → OrgPullData
                                          └ OrgSyncApplier.apply(data)  [@Transactional]
```

`OrgSyncApplier` 独立成 Bean 是为了让 `@Transactional` 真正生效（同类内部调用会被代理绕过）。

### 6.2 落库规则

| 对象 | 匹配键 | 规则 |
|---|---|---|
| 部门 | `corp_id` | upsert；第二遍回填 `parent_id`；`isSystem=1` 的默认部门不改不删 |
| 用户 | `external_id` | upsert；`deletedTime` 非空 → `DISABLED` |
| 用户-部门 | `(user_id, dept_id)` | 先清后建（保证组织调整后不残留旧关系） |
| 角色 | `role-map` | 未命中 → `COMMON` |

### 6.3 防误清（重要）

当拉取结果中 **users 或 depts 为空**时，**跳过**「缺失者置 DISABLED / 缺失部门软删」这一步。

原因：Casdoor 侧权限不足、网络抖动、`owner` 配错都可能返回空数组；若无条件执行清理，会把全公司账号一次性禁用。宁可漏清理，不可误清理。

### 6.4 历史与状态

- `OrgSyncState`（内存）：`running` / `lastSyncAt` / `lastResult`，供 `/org/sync/status` 快速读取。
- `org_sync_log`（表）：每次同步一条 `RUNNING → SUCCESS|FAILURE`，含 `total/inserted/updated/skipped/error_message`。
- `GET /org/sync/logs`（ADMIN/AUDITOR）、`GET /org/sync/latest`（ADMIN/AUDITOR/USER_MANAGER）。

### 6.5 Casdoor 管理 API 鉴权

`/api/get-users`、`/api/get-groups`、`/api/get-organizations` 需要 Application 凭据。实现上**同时**携带：

- 查询参数 `clientId` + `clientSecret`（Casdoor 官方 SDK 的做法）；
- HTTP `Authorization: Basic base64(clientId:clientSecret)`（部分版本优先读 Basic）。

分页：`p` 从 1 递增，`pageSize=batch-size`，返回条数不足一页即结束（上限 200 页，防死循环）。

---

## 七、后台 `/admin`

### 7.1 布局与路由

独立布局 `AdminLayout`（左侧固定导航 + 顶部标题条），**不带**任务左栏 `BbFilterRail`。

| 路径 | 页面 | 可见角色 |
|---|---|---|
| `/admin` | 概览（登录方式 / 同步状态 / Casdoor 地址 / 最近登录 / 快捷入口） | ADMIN、AUDITOR、USER_MANAGER |
| `/admin/users` | 用户与机构（左部门树 + 右用户表，Excel 导入导出） | ADMIN、USER_MANAGER |
| `/admin/casdoor` | Casdoor 对接（连接参数 + 登录方式 + 同步策略 + 连通性测试 + 立即同步） | ADMIN |
| `/admin/logs` | 日志（登录/退出、操作、组织同步、调度任务） | ADMIN、AUDITOR |
| `/admin/settings` | 常规设置（登录安全、审计留存、文件存储） | ADMIN |

旧路径兼容：`/admin/org → /admin/users`、`/admin/audit → /admin/logs`（路由重定向，不返回 404）。
前台左栏底部由两个图标（组织管理 / 日志）合并为单一「后台管理」入口 `[data-test="nav-admin"]`。

### 7.2 表单完全由后端驱动

`ConfigGroupForm.vue` 不硬编码任何字段：按 `GET /admin/config/{group}` 返回的 `type` 渲染控件（`SELECT`→下拉、`BOOL`→开关、`INT`→数字、`JSON`→文本域、其余→输入框），并展示 `source` 徽标（已保存 / 配置文件 / 默认值）与「需重启」提示。

新增配置项 = 在 `ConfigDefinition` 加一行，前后端零改动。

### 7.3 日志页

用户明确要求「系统运行日志不要，但要 Casdoor 的同步、登录、退出的日志」，因此：

- **不做** logback 文件日志查看；
- 做四类业务日志：登录/退出（可按 `provider`、`eventType` 筛选）、操作日志、组织同步日志、调度任务日志（仅 ADMIN）。

---

## 八、契约与验证

### 8.1 openapi

新增/扩展端点：`/auth/oidc/redirect`、`/auth/oidc/callback`、`/org/sync/logs`、`/org/sync/latest`、`/jobs/logs`、`/admin/config`、`/admin/config/{group}`（GET/PUT）、`/admin/config/casdoor/test`；`LoginLogVO` 增 `provider/eventType/externalId`；新增 `ConfigItemVO/ConfigGroupVO/ConfigSaveReq/ConfigTestResult/OrgSyncLogVO/JobLogVO` 及其分页壳。

前端类型由 openapi 生成（`pnpm gen:types`），api 层再对 optional 字段做一次归一化（`adminConfig.ts`），视图层不必到处写 `?? ''`。

### 8.2 验证结果

| 项 | 结果 |
|---|---|
| `mvn -o test` | **29/29 通过**（8 个 `CasdoorIntegrationTest` 用例） |
| `vue-tsc --noEmit` | 无错误 |
| `vitest run` | 6/6 通过 |
| `vite build` | 成功，产出 `AdminLayout/AdminOverview/AdminLogs/CasdoorConfig/ConfigGroupForm/AdminSettings` 分包 |
| `mvn -o package` + jar 启动 | 成功：内嵌 86 个静态文件、V1–V5 迁移全部执行、`/admin*` 深链全部回退 `index.html` |
| Playwright（MSW） | 30/30 通过 |
| Playwright（真实 jar 联调，选入式） | **15/15 通过** |

`CasdoorIntegrationTest` 覆盖：非 ADMIN 访问配置接口被拒、六个分组齐全且密文只返掩码、保存密文后 `hasSecret=true`、回传掩码不覆盖原值、切 OIDC 后 `external/config` 返回 `authorizeUrl`/`logoutUrl`、**授权地址参数正确编码**、SELECT 非法取值被拒、同步/任务日志可读、非 PULL 模式同步为无害空操作。

打包冒烟额外确认（真实 jar，非 Mock）：

- `PUT /admin/config/casdoor` 落库后 `source=DB`、`client-secret` 回 `******` 且 `hasSecret=true`；
- `GET /auth/external/config` 在 OIDC 下返回可用 `logoutUrl`；
- `GET /auth/oidc/redirect?redirect=/admin` → 302，参数编码完整；
- `POST /org/sync`（Casdoor 不可达）→ `20008` 带具体原因，**用户与部门数量未变**（防误清生效），`org_sync_log` 记 `FAILURE/CASDOOR`；
- `/audit/logins` 正常返回并带 `provider/eventType`。

### 8.3 收尾阶段发现并修复的缺陷

| 缺陷 | 影响 | 修复 |
|---|---|---|
| `CasdoorClient.authorizeUrl()` 用 `UriComponentsBuilder.build(true)` | `scope` 含空格、`redirect_uri` 含 `://`，被当作「已编码」校验失败抛 `IllegalArgumentException`，兜底成 `10001`——**授权页永远打不开，OIDC 登录完全不可用** | 改为逐参数 `URLEncoder` 手工拼接；`OidcLoginController.redirect()` 捕获异常后走 `fail()` 提示而非吐 JSON 错误码。新增 `oidcRedirectBuildsEncodedAuthorizeUrl` 回归用例锁死 |
| `e2e-real/ui.real.spec.ts` 断言左栏 6 个视图计数 | 提交 `d48d8c5` 移除「未来7天任务」视图后计数变 5，断言未同步，**8 个真实联调用例全部卡在登录后** | 抽出 `VIEW_COUNT` 常量（注释标明与 `BbFilterRail.navItems` 长度绑定） |
| `#11 子任务状态可切换` 断言只认「变完成」 | 该交互是**切换**语义；真实库会被上一轮运行写脏，第二次跑点击即取消完成（`1 -> 0`），断言必然失败 | 改为断言「状态确实翻转」（`doneAfter !== doneBefore`），对初始状态免疫 |
| `latestOrgSync()` 返回类型 `| null`，实际可能 `undefined` | 服务端 `non_null` 序列化在无记录时省略 `data`，类型与运行时不一致 | api 层收敛 `data ?? null` |

---

## 九、与原「评估版方案」的差异

| 早期假设 | 实际实现 | 原因 |
|---|---|---|
| 引入 `spring-boot-starter-oauth2-client` + `webflux(WebClient)` | **未引入**，改用 `RestClient` 手写授权码流 | 只需 authorize/token/userinfo 三个调用，引整套 oauth2-client 会与自有 JWT 体系打架；单制品 jar 也不必为 WebClient 拉 reactor |
| 配置写在 `application.yml` 的 `app.orgsync.casdoor.*` | 配置存 **`sys_config`**，可在后台改、密文加密 | 用户明确要求「Casdoor 配置信息不放在 yml」 |
| 不新建同步日志表（`OrgSyncState` 够用） | 新建 `org_sync_log` | `OrgSyncState` 是内存态、重启即丢，日志页需要历史 |
| `external_source` 可选 | 已加列并使用 | 为多身份源预留，成本极低 |
| 前端「系统管理」页 + 可选同步状态页 | `/admin` **独立布局** + 5 个页面 | 用户拍板独立后台更清爽 |
| 前端 `views/login/` | `views/auth/` | 路径本就如此 |
| provider 新增 `CASDOOR` 值 | 沿用 `OIDC` | 改动最小，Casdoor 就是 OIDC IdP |

另外修正了一个隐藏缺陷：`OrgSyncJob` 读 `app.job.orgsync-cron`，而 `application.yml` 写的是 `app.job.org-sync-cron`，键名不一致会导致 cron 配置**静默失效**（一直用默认值）。已统一为 `orgsync-cron`。

---

## 十、首次接入操作手册

1. **建 Application**：Casdoor 控制台创建 Application，记下 `Client ID` / `Client Secret` / `Organization`。
2. **配回调**：Application 的 Redirect URLs 填 `http://<host>/api/v1/auth/oidc/callback`（与后台「回调地址」一字不差，含协议与端口）。
3. **建角色**：在 Casdoor 建 `bluebird-admin` / `bluebird-auditor` / `bluebird-user-manager` / `bluebird-common`，把用户挂上去。
4. **填后台**：登录 BlueBird（admin）→ `/admin/casdoor` → 填连接参数 → 「测试连通性」应返回 issuer 与两个端点。
5. **试同步**：把「同步模式」设为 `PULL` → 「立即同步」→ 到 `/admin/logs` 看组织同步记录，核对 `total/inserted/updated`。
6. **切登录**：确认同步结果无误后，把「认证方式」改为 `OIDC`。此时普通用户只能走 SSO，admin 仍可账密应急。
7. **配定时**：`orgsync.cron` 默认 `0 0 1 * * *`（每日 01:00，Asia/Shanghai）；**改 cron 需重启**。

**主密钥**：生产环境请显式设置 `BB_CONFIG_SECRET`（Base64 的 32 字节或任意字符串经 SHA-256 派生）。不设置则自动生成 `./data/.config-key`——该文件**必须**与数据库一同备份，丢失后已存密文无法解密（需重新填写 client-secret）。

**回滚**：把「认证方式」改回 `LOCAL` 即可恢复全员账密登录，无需重启、无需改代码。

---

## 十一、已知限制

1. **单实例假设**：`OrgSyncState` 与同步锁 `ReentrantLock` 都在进程内（ADR-016 单实例 SQLite 部署）。多实例部署需换分布式锁。
2. **同步是全量对齐**：每次拉全量用户再 upsert，Casdoor 用户量很大（>5 万）时需评估耗时；`batch-size` 只影响分页大小，不改变总量。
3. **不做增量删除**：Casdoor 侧删除的用户只在「本次拉取非空」时被置 `DISABLED`，不会物理删除（保留历史任务 owner）。
4. **`dept-source=organizations` 语义有限**：Casdoor 的 Organization 通常只有一个层级，多层级组织树建议用 `groups`。
5. **token 不续期上游会话**：本地 refresh token 独立于 Casdoor，Casdoor 侧登出不会立刻使本地 token 失效（反之亦然）。当前以「登出时一并跳上游登出」缓解。
6. **回调地址推导依赖反代头**：未显式配置 `redirect-uri` 时按 `X-Forwarded-Proto/Host` 推导，反向代理需正确传递这两个头。
