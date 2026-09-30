# BlueBird 重构 · 决策记录（ADR）

> 上级文档：`01蓝鸟重构方案.md`、`02后端模块详细设计.md`、`03前端模块详细设计.md`
> 说明：本文为架构决策记录（ADR）留痕，固化评审已确认的关键决策，供实施与后续追溯。
> 评审来源：评审留痕（历次 02xx 评审/审核意见，如 `0204评审_一审意见.md` … `0217评审_十三审意见.md`）存**外部评审工作区**，**本仓 `Task/` 不纳入**（本仓仅 `01`–`06`）。（修订 F10/十三审：原引用的 `02模块详细设计_审核意见.md`、`04复审意见.md` 已随命名调整不存在。）

---

## ADR-001 · 整体技术选型与 Monorepo

- **状态**：已确认
- **背景**：旧系统为前后端分仓（Spring Boot 3.2 + 若依 Vue3），需整体重写。
- **决策**：
  - 新建 Git 仓库，**Monorepo 单仓库**（`backend/` + `frontend/` + `deploy/` + `docs/`）。
  - 后端 Java 21 + Spring Boot 3.3 + MyBatis-Plus + Flyway（+ `flyway-database-sqlite`）+ **SQLite 3（嵌入式，无外部 DB / 无缓存）**（见 ADR-016，取代本 ADR 原 PG/Redis 选型）。
  - 前端 Vue 3 + Vite 5 + TypeScript + Pinia + Element Plus + Tailwind。
  - 私有化部署（Linux、无 K8s），支持 Docker Compose 与手动 jar 两种方式。
  - **开源协议**：Apache-2.0（`LICENSE` 为必交付件）。
- **后果**：团队沿用主栈，风险最低；密钥从空库起步，历史不复制。

> 注：`docs/adr/` 目录规划在 M0 产出（本文件先行于 `Task/` 目录落地）。

---

## ADR-002 · 数据迁移破坏性决策（B3/B5）

- **状态**：已废弃（ADR-007 取代）
- **背景**：原决策处理旧→新数据迁移的语义/数据丢失口径。
- **决策**：因 ADR-007 确认不迁移旧数据（空库起步，旧系统废弃），本 ADR 全部失效。
- **后果**：无迁移即无数据保真问题；原「task_user 历史归并」「多分类→单分类迁移口径」等不再适用。设计决策本身（单一分类、无手动排序）保留于 `01` §8.3。

---

## ADR-003 · RBAC 权限模型简化（N1）

- **状态**：已确认（复审新增，升格为破坏性决策）
- **背景**：旧库存在完整 RBAC 多对多（`sys_role`/`sys_role_auth`/`sys_user_role`/`sys_user_auth`/`sys_auth`）；新模型坍缩为 `sys_user.role_code` 单列（ADMIN/COMMON）。
- **决策**：
  - 新系统不建 `sys_role`/`sys_role_auth`/`sys_user_role`/`sys_user_auth`/`sys_auth` 等表，仅用 `sys_user.role_code` 单列。
  - 按钮级权限本期不做，`sys_permission` 不建；如后续需要再补。
  - **修订 0306/Q1**：`role_code` 取值由 `ADMIN/COMMON` 扩为**四值枚举** `ADMIN / AUDITOR / USER_MANAGER / COMMON`，用于**职责分离**（`AUDITOR` 仅读审计日志、`USER_MANAGER` 管用户/组织），**仍不建角色表**（单值枚举，不违背本 ADR 的「无多角色表」原则）。
- **后果**：丢失「一个用户多角色」「角色含多权限」能力；`@PreAuthorize` 仅做角色级判断。
> 修订 ADR-007：因不迁移旧数据，原「旧四张关联表丢弃、按管理员标记映射」的迁移口径不再适用；RBAC 简化决策本身保留。

---

## ADR-004 · schema 单一真相源（B1）

- **状态**：已确认
- **背景**：旧仓库存在多份矛盾 schema（根 `sql/blue_bird.sql` 缺表、`resources/sql/02_create_tables.sql` 最完整但从未被加载、`sql/main.sql`+`data2.sql` 为 SQLite），且 classpath 上无可加载权威 schema。
- **决策**：以 `01` §8 DDL + Flyway `V1__init.sql` 为唯一真相源，M0 冻结；删除/归档 SQLite 与残缺 SQL；`spring.sql.init` 弃用，schema 完全交由 Flyway。
- **后果**：纯净部署不再缺表；新系统空库起步，schema 完全由 Flyway 管理（ADR-007）。

---

## ADR-005 · 成功码与响应体统一（C4）

- **状态**：已确认
- **决策**：新系统成功码统一为 `0`（旧 `ResponseResult` 为 200/500）。因旧系统直接停用、新系统重新部署（ADR-007），不存在新旧并行/双跑期，无需兼容旧 200 成功码。
- **后果**：不长期兼容旧 200 成功码。

---

## ADR-006 · 外部身份源（部署时选定，不可变更）

- **状态**：部分取代（认证/同步架构由 ADR-008 重构；「部署时选定不可变」「不加 external_source 列」仍有效）
- **背景**：系统需支持接入外部身份源，**同一部署只启用一个外部源**，**部署时选定后不可变更**（如需更换须重新部署）。自身账号密码登录始终保留（且为**默认**——`provider=LOCAL`，仅本地账密，外部源可选，见 ADR-008）。
- **仍有效决策**：
  1. **部署时选定不可变**：`app.identity.provider` 部署时确定，运行期不切换；更换须重新部署。取值 `LOCAL|OIDC|WECHAT`（ADR-008）。
  2. **数据模型**：`sys_user.external_id` 记录外部源用户标识，不加 `external_source` 列（部署时即确定，无需运行时区分）。
  3. **前端**：`GET /api/v1/auth/external/config` 返回当前 provider 类型 + 前端渲染所需配置；`LOCAL` 时不渲染外部登录入口。
- **被 ADR-008 取代的决策**：
  - ~~`ExternalIdentityAdapter` 接口 + 每 IDP 一个 Adapter 实现~~ → ADR-008 改为 OIDC 统一认证（Spring Security OAuth2 Client 配置式），企微例外保留 `WechatIdentityAdapter`；默认 `LOCAL` 不装配外部源。
  - ~~`app.identity.provider=WECHAT|DINGTALK|FEISHU|GOOGLE`~~ → ADR-008 简化为 `LOCAL|OIDC|WECHAT`（`LOCAL` 为默认，仅本地账密）。
  - ~~`ExternalIdentityAdapter.syncOrg()` 拉取模式~~ → ADR-008 改为组织同步独立配置（SCIM/PULL/PUSH/NONE 四模式）。

---

## ADR-007 · 不迁移旧数据（空库起步，旧系统废弃）

- **状态**：已确认
- **背景**：经确认，新系统**不存在新旧数据迁移问题**——旧系统不再继续运行，旧系统历史数据完全不要，新系统空库起步。
- **决策**：
  1. **不迁移**：新系统以 Flyway `V1__init.sql` 建空库，不编写任何 ETL/迁移脚本，不保留旧库 → 新库的 ID 映射表。
  2. **数据初始化**：部署后由 ADMIN 手工建用户 + 外部身份源通讯录同步（`OrgSyncService` 每日 01:00 或手动触发）。
  3. **旧系统废弃**：旧系统不再并行运行，无双跑期、无新旧前端切换过渡。
  4. **前序 ADR 调整**：ADR-002（迁移破坏性决策）、ADR-003（RBAC 简化）、ADR-004（schema 真相源）中涉及「迁移」的表述已失效，保留决策本身（RBAC 简化、schema 单一真相源仍有效），但迁移口径不再适用。
- **后果**：
  - 无迁移工作量、无迁移风险、无数据保真问题（如旧 `task_user` 无 role 的 ASSIGNEE/CC 归并问题不再存在）。
  - 里程碑 M5 由「迁移+联调」改为「联调+压测+上线」。
  - 成功标准中「历史口径限定」「迁移核对预期」等描述全部删除。
  - 用户/组织数据依赖 ADMIN 手建 + 外部源同步，首次部署需确保外部源可连通。

---

## ADR-008 · 身份对接架构重构（OIDC 统一认证 + 组织同步解耦）

- **状态**：已确认
- **背景**：ADR-006 最初按「每个 IDP 一个 Adapter」设计（WechatIdentityAdapter / DingtalkIdentityAdapter / FeishuIdentityAdapter / GoogleIdentityAdapter）。随着需求扩展至 Keycloak / Casdoor / 竹云 / Azure / Okta 等，每新增一个 IDP 就写一个 Adapter，复杂度线性增长，不可持续。
- **决策**：参考 GitLab / Grafana 等主流软件实践，重构为「**标准协议为主 + 定制为辅**」：
  0. **默认本地账密**：`app.identity.provider=LOCAL`（**默认**）——仅本地账号密码登录，不装配任何外部源，适合不需要对接统一身份源的私有化/单机部署；OIDC / WECHAT 为**可选**，仅在需要时切换开启。`external/config` 返回 `provider=LOCAL` 时前端不渲染外部登录入口。
  1. **认证层（可选启用）统一走 OIDC**：使用 Spring Security OAuth2 Client，配置式接入任意 OIDC 兼容 IDP（Google / Keycloak / Casdoor / 竹云 / Azure / Okta / 钉钉 / 飞书等），**零代码**。仅企业微信因 OIDC 支持不完整保留 `WechatIdentityAdapter` 作为例外。OIDC / WECHAT 仅在 `provider` 选中时装配（`@ConditionalOnProperty`）。（注：本 ADR 既定 OIDC 统一认证架构不变，`LOCAL` 只是新增一个关闭外部源的默认取值。）
  2. **组织同步与认证解耦**：独立配置 `app.orgsync.mode`，四种模式可插拔，**部署时选定不可变更**（与 `app.identity.provider` 同一约束，更换须重新部署，ADR-006/ADR-007）：
     - `SCIM`：暴露 `/api/v1/scim/v2/*` 标准端点，被动接收 SCIM 2.0 兼容 IDP（Keycloak / Casdoor / Okta / Entra ID）推送，**零代码**。
     - `PULL`：定时主动拉取，适配企微 / 钉钉 / 飞书（各写 OrgSyncAdapter）。
     - `PUSH`：暴露 `/api/v1/org/push` 接收端点，适配竹云等私有 IDM 推送（写定制 OrgPushAdapter）。
     - `NONE`（**默认**）：不同步，用户由 ADMIN 手工创建；若启用 OIDC 也可首次登录自动建号（`role_code=COMMON`，`dept_id=NULL`），ADMIN 手动分配部门。
  3. **用户自动建号策略**：OIDC 用户首次登录时，若 `sys_user` 无对应 `external_id` 且 `app.identity.auto-create-user=true`，则自动建号（`role_code=COMMON`，`dept_id=NULL`）；企微保持「不自动建号」（须先同步或 ADMIN 手建）；`LOCAL` 无外部源、无自动建号。
  4. **`app.identity.provider` 简化**：从 `WECHAT|DINGTALK|FEISHU|GOOGLE` 简化为 `LOCAL|OIDC|WECHAT`（`LOCAL` 默认仅本地账密；OIDC 覆盖所有标准 IDP，企微单独）。
- **后果**：
  - 默认 `LOCAL` 下系统**只支持本地账号密码**，不依赖任何外部 IDP，私有化部署最简；需要对接统一身份时再切换 `OIDC` 或 `WECHAT`（重新部署）。
  - 认证层新增 OIDC IDP **零代码**（仅配置 `issuer/clientId/clientSecret`）。
  - 组织同步按协议类型分四种模式，标准协议（SCIM）零代码，私有协议写一个 Adapter。
  - ADR-006 中「每 IDP 一个 Adapter」「`@ConditionalOnProperty` 单选」「`ExternalIdentityAdapter.syncOrg()` 拉取模式」等设计被本 ADR 取代。
  - 企微作为唯一非标准例外保留单独 Adapter。
- **关系**：取代 ADR-006 的 Adapter 架构；ADR-006 的「部署时选定不可变」「不加 external_source 列」仍有效。

---

## ADR-009 · 前端产物内嵌后端 jar（单制品部署，不使用 Nginx）

- **状态**：已确认
- **背景**：新项目以私有化/单机部署为主，运维要求「**只发一个 jar**」即可运行，免去 nginx + 前端静态目录的分离部署与版本同步。旧系统实测本即以 jar 内 `static/index.html` 形式提供过前端页面。
- **决策**：
  1. **单制品**：前端 `pnpm build` 的 `dist` 在**打包期**注入 `backend/src/main/resources/static/`，随 jar 发布；对外**只有一个 `bluebird-task.jar`**。
  2. **不使用 Nginx**：页面与 `/api/v1` 由内嵌 Tomcat **同源**提供；TLS/限流等如需，交由外部网关，不在本项目制品内。
  3. **SPA history 回退**：新增 `SpaForwardController`，对「非 `/api/v1/**`、非静态资源、`Accept: text/html`」的请求回退 `index.html`；**API 的 404 仍返回 JSON `ApiResult`**，不被回退吞掉。
  4. **静态资源策略**：`/assets/**` 长缓存 `immutable`；`/index.html` `no-cache`；`server.compression.enabled=true` 替代 Nginx gzip。
  5. **构建接线**：Docker 多阶段（node 构建前端 → maven 内嵌打包），Dockerfile 位于 `backend/Dockerfile`、**构建上下文=仓根**；容器外由 CI/本地先构建前端再 `mvn package`（`maven-resources-plugin` 拷贝 dist）。
  6. `frontend/dist`、`backend/src/main/resources/static/` 为构建产物，**不入 git**。
- **后果**：
  - 部署/回滚简化为「替换一个 jar」；同源，无 CORS。
  - 前端与后端**同版本发布**（不可独立升级前端）；不支持 CDN/边缘缓存；jar 体积增大约数 MB。
  - 开发期不受影响：仍用 Vite dev server + 代理联调。
  - `deploy/` 中原独立 `frontend`、`nginx` 服务移除（见 `01 §11.1`）。
- **关系**：落地于 `01 §6`（仓结构）、`01 §11`（DevOps）、`02 §1.7.1`、`03 §3.2/§8`；与 ADR-007（空库起步）无冲突。

---

## ADR-010 · 运行时配置释放（config extraction）

- **状态**：已确认
- **背景**：交付物为「单个 jar」，但用户要求**运行时可修改配置**、避免每次改配置就重打包。旧系统已用 `BlueBirdApplication.checkAndCopyConfig()` 实现「tar 内默认配置复制到运行目录」模式，已验证可行。
- **决策**：
  1. **默认内置**：jar 内打包一份默认配置 `classpath:/application.yml`（**不含真实密钥**，仅占位符 / 本地默认）。
  2. **首次释放**：启动时（`ConfigFileReleaser`，`ApplicationRunner`）检测运行目录（= jar 所在目录 / CWD）的 `application.yml`，不存在则复制内置默认过去；**已存在则不覆盖**。
  3. **后续读取**：Spring 默认优先级 `file:./application.yml` > `classpath:/application.yml`，用户改外部文件**重启即生效**（无需重打包）。
  4. **升级不覆盖**：新 jar 替换旧 jar 后，运行目录已有配置**保留**。
  5. **敏感值不入内置**：内置默认配置**不含真实密钥**，仅保护 `${...}` 占位。可自生成的密钥由首次运行**自动生成**（见本 ADR §6：`JWT_SECRET`、`BOOTSTRAP_ADMIN_*`）；必须与外部系统一致的（`SQLITE_URL`（可选）、`OIDC_CLIENT_ID/SECRET`、`WECHAT_CORP_SECRET`、`ORGSYNC_PUSH_TOKEN`）用 `${...}` 占位，由环境变量 / systemd `EnvironmentFile` 注入，用户如需明文自行写运行目录文件并控权限。**自生成项亦允许环境变量覆盖**（部署方显式指定则用之）。**去 PG/Redis（ADR-016）后，无 DB/缓存连接串需注入**。
     > **空值覆盖保护**：自动生成的密钥写入**释放出的 `./application.yml`**（`chmod 600`），**不在 `.env` 留空占位**。Spring 属性优先级为「OS 环境变量 > `file:./application.yml`」——若经 `EnvironmentFile`/Compose `env_file` 注入 `.env` 中留空的 `JWT_SECRET=`/`BOOTSTRAP_ADMIN_PASSWORD=`，空串会**覆盖自动生成值**，使首次 ADMIN 回落人工引导。`.env` 仅承载外部注入类；需自定义自生成密钥时才填**非空**值（见 `01 §15.1`）。
  6. **密钥分类生成**（能自生成的才首次生成；必须与外部系统一致的只留占位）：
     - **自生成**（首次跑随机生成，写入释放出的配置文件，`chmod 600`，不入仓库/内置默认）：
       - `JWT_SECRET`：首次生成随机 32B（HS256，≥256bit），预填，每套部署唯一；
       - `BOOTSTRAP_ADMIN_PASSWORD`：首次生成强口令，控制台/受保护日志打印一次，`must_change_password=1` 强制首登改密。
     - **外部注入**（首次跑只留 `${...}` 占位，本地不生成）：`SQLITE_URL`（可选）、`OIDC_CLIENT_ID/SECRET`、`WECHAT_CORP_SECRET`、`ORGSYNC_PUSH_TOKEN`——必须与外部系统/部署一致，由用户配置或 `EnvironmentFile`/Compose 注入。
     - **数据库**：SQLite 嵌入式（ADR-016），**无口令、无外部连接串**；`.db` 文件随 jar 目录自建（默认 `./data/bluebird.db`）。
  7. **配置同时承载日志/存储目录**：释放出的 `application.yml` 含 `logging.file.name`（日志目录）与 `app.storage.root`（文件存储目录），首次默认值即可用，允许用户按需修改。
  8. **配置丢失的自愈**：若运行目录配置文件被删，下次启动重新释放并重生成 `JWT_SECRET` → 已签发的 refresh token 失效、用户需重新登录（可控，重启即可自愈）。
- **后果**：
  - 交付物仍是单个 jar，配置零打包修改；升级时用户配置持久。
  - 运行方式须固定 CWD=jar 目录（`cd <dir> && java -jar bluebird-task.jar`）。
  - 明文写入运行目录的密钥不再受仓库保护，运维需保证目录权限（`chmod 600`）。
- **关系**：落地于 `01 §11.7`、`02 §1.8.1`；与 ADR-009（单制品）、ADR-002 安全、ADR-004（单一真相源）互补，不冲突。

---

## ADR-011 · 生产安全与精简

- **状态**：已确认
- **背景**：在「不做大、保证功能与安全」前提下收口技术栈，并防止接口信息在生产暴露。
- **决策**：
  1. ~~**PG 最低支持 13**~~：**已由 ADR-016 取代**——数据库改 SQLite，不再涉及 PG 版本兼容。
  2. **接口文档生产关闭**：springdoc 由 `springdoc.api-docs.enabled`/`swagger-ui.enabled` 统一受 `DOC_ENABLED`（默认 `false`）控制；**生产关闭** `/v3/api-docs` 与 `/swagger-ui`，二者同时从免鉴权白名单移除；仅 dev 联调 `DOC_ENABLED=true`。契约以 `docs/api/openapi.yaml` 静态文件为准（前后端生成/联调用它，不依赖运行时 doc），故关闭运行时不损失契约能力。
  3. **去掉 IP 属地定位（ip2region）**：登录日志/操作日志不再采集 `address` 属地，`audit_operate_log`、`audit_login_log` 移除 `address` 列；`IpUtils` 仅取 IP。精简离线库依赖。
  4. **OIDC / 企微依赖**：`spring-security-oauth2-client`、`wx-java-cp` 为**可选依赖**（Maven profile / 条件装配），默认 `provider=LOCAL` 打包时不引入，进一步瘦身；选用时再启用。
  5. **前端 Mock 二选一**：保留 **MSW**，去 Prism（功能等价，避免双方案）。
- **后果**：
  - 生产不泄露接口文档；契约能力不损失（静态 `openapi.yaml`）。
  - 依赖与镜像体积减小（**DB/缓存外部依赖已随 ADR-016 全部移除**）。
  - 登录/操作日志不含 IP 属地信息（仅保留 IP 与 UA）。
- **关系**：落地于 `01 §5/§7.5/§7.6/§7.9/§15.1`、`02 §1.7/§1.8/§1.10/§6.2-§6.4`、`03 §3.1`；与 ADR-001（选型）、ADR-009（单制品）、ADR-010（配置释放）一致。

---

## ADR-012 · 组织级只读可见性（主管可见下属任务）

- **状态**：已确认（0305/D1）
- **背景**：企业级评审（`0305审核_用户需求与操作逻辑`）指出旧系统任务可见性仅按 `belongUserId`/`executeUserList`，缺组织级规则；产品确认**主管可见下属任务**。
- **决策**：
  1. `sys_department` 增 `leader_user_id`（**单一负责人**，可空；一名用户可为多个部门的负责人）。
  2. 「下属集合」= 该用户为负责人的**全部部门及其递归子部门**内的所有用户（经 `sys_user.dept_id` / `sys_user_department` 解析，**排除本人**）。
  3. **只读可见性**：当前用户对任务 T 可见 ⟺ `T.owner=me` ∨ `me∈participants(T)` ∨ `ADMIN` ∨ `T.owner/参与人 ∈ subordinates(me)`。**可见 ≠ 可写**：写权限仍按 RF3（`owner/assignee/cc/ADMIN`）；主管对下属任务**只读**。
  4. 列表显式筛选：`GET /tasks?scope=all&subordinate=true`（默认 `false`，不改动六大视图）；`/tasks/count` **不**计入下属任务。
  5. 读鉴权 `TaskAuthService.assertVisible`；部门负责人/组织变更后下属集合（如启用进程内缓存）失效。默认直算（去缓存，ADR-016）。
- **修订（0306/Q3）**：
  - 组织级可见性受 **`app.security.manager-can-read-subordinate` 开关控制，默认 `false`（关闭）**；开启须在部署侧**明示告知**并记录 PIPL 依据（见 `06合规对照表.md`）。
  - **敏感读审计**：主管读取「非本人参与」的下属任务记 `audit_operate_log(action=read_subordinate)`。
  - **（可选）缓存失效事件驱动**：若启用进程内缓存，部门树变更 / `leader_user_id` 变更发布领域事件显式清除；默认直算（去缓存，ADR-016）。
- **后果**：
  - 主管获得**只读**的下属任务视图；不引入部门级写权限，避免越权面扩大。
  - 需维护部门负责人数据；「下属」递归计算默认直算（去缓存，ADR-016；如启用进程内缓存则部门树变更时失效）。
  - 隐私提示：下属的个人任务对主管**只读可见**，属产品明示决策；若后续需收紧（如仅 owner 任务、或加开关），另行 ADR。
- **关系**：落地于 `01 §3/§8.2/§8.3#7/§9.2/§12.1`、`02 §3.2/§3.4/§4.4/§4.5/§4.7`、`03 §5.3.1/§5.4`、`05需求覆盖矩阵.md`；与 ADR-003（RBAC 简化）、ADR-008（组织同步）互补，不冲突。

---

## ADR-013 · 并发控制（乐观锁）

- **状态**：已确认（0306/Q4）
- **背景**：企业级评审（`0306审核_蓝鸟重构方案-企业级审核`）指出任务无并发控制，两人同时编辑会**后写覆盖前写**（Lost Update）且无冲突提示——协同场景数据正确性硬伤。
- **决策**：
  1. 主实体 `task` / `category` / `task_menu` 等增 `version INTEGER NOT NULL DEFAULT 0`（MyBatis-Plus `@Version`）。
  2. `PUT /tasks/{id}`、`complete` / `uncomplete` 等更新须携带 `version`；版本失配返回 `code=10006 VERSION_CONFLICT`（**HTTP 仍 200**，遵循 ADR-005 统一响应约定；不引入 409 以保持前端拦截器单一约定）。
  3. 前端收到 `10006` → 提示「内容已被他人修改，请刷新」并重拉最新版本。
- **后果**：消除协同编辑覆盖；客户端须回传版本号（`openapi` 同步 `TaskVO.version`）。**实时协同（WebSocket/SSE）列二期**。
- **关系**：落地于 `02 §4.2/§4.5/§4.7`、`03 §4.2`、`docs/api/openapi.yaml`（`TaskVO.version`）、`05需求覆盖矩阵.md`。

---

## ADR-014 · 企业级安全与合规基线

- **状态**：已确认（0306/Q1/Q3/Q5/Q6/Q7/Q8）
- **背景**：企业级评审列出权限/审计/安全/工程多维度缺口，需在「不做大」前提下取舍。
- **决策**：
  1. **职责分离（Q1）**：`role_code` 扩为 `ADMIN/AUDITOR/USER_MANAGER/COMMON`（见 ADR-003 修订），`AUDITOR` 只读审计日志。
  2. **CC 只读（Q2）**：`CC` 为知会只读；写权限仅 `owner/assignee/ADMIN`（修 `02 §4.7`）。
  3. **审计可靠性（Q3/Q8）**：一般日志**本地缓冲双写**异步入库；关键事件（登录成败/锁定、删除、用户禁用、权限/角色变更、`leader_user_id` 变更、敏感读）**同步落库**；保留期 ≥180 天；表只追加；防篡改（hash 链/WORM）列二期。
  4. **安全基线（Q5）**：本期做**登录防暴破**（失败计数+锁定，`20006`）与**安全响应头**（HSTS/CSP/X-Frame-Options/X-Content-Type-Options/Referrer-Policy）；**密码策略 / 附件病毒扫描 / 存储加密 / 强制 HTTPS-only 列二期**。
  5. **调度可观测（Q6）**：`sys_job_log` 执行历史 + 失败告警 + 手动重跑；cron/时区外部化；可观测性（Metrics/Tracing）与多实例 leader 选举列二期。
  6. **合规对照（Q7）**：输出 `06合规对照表.md`（等保 2.0 三级 + PIPL，含「主管读下属任务」的个人信息处理依据）。
  7. **契约一致性 CI（Q6）**：`scripts/check-contract.mjs` 校验 `openapi.yaml` ↔ 模块接口表/ErrorCode 枚举一致，不一致即红。
- **后果**：提升安全与合规；引入 `sys_job_log`、新错误码（`10006/10007/20006`）、角色枚举、安全头配置与 CI 契约检查。
- **关系**：落地于 `01 §3/§7.7/§7.9/§11.5/§12/§12.1`、`02 §1.4/§1.11/§2.3/§2.4/§2.7/§4.7/§6`、`03 §4.2/§5.3.1/§5.6`、`06合规对照表.md`、`05需求覆盖矩阵.md`；与 ADR-003/ADR-012/ADR-013 互补。

---

## ADR-015 · 分类共享范围（个人 / 部门 / 组织）

- **状态**：已确认（0308 产品决策；回应 0306/P1 缺口）
- **背景**：企业级审核（`0306`）指出 `category.owner_id` 仅个人，无组织/部门级共享分类体系，限制协同与统计。
- **决策**（一期做，保持最小）：
  1. `category` 增 `scope TEXT NOT NULL DEFAULT 'PERSONAL'`（`PERSONAL`/`DEPARTMENT`/`ORG`）与 `dept_id INTEGER NULL`（仅 `DEPARTMENT` 使用）；`owner_id` = 创建者。
  2. **读可见**：`PERSONAL` → `owner_id=我`；`DEPARTMENT` → `dept_id ∈ 我所在部门`；`ORG` → 全部登录用户。`GET /categories` 返回三者合并树，`?scope=` 可过滤。
  3. **写**：`PERSONAL` → 创建者；`DEPARTMENT` → 创建者 / 该部门负责人（`leader_user_id`）/ `ADMIN`；`ORG` → `ADMIN`/`USER_MANAGER`。`CategoryAuthService.assertWritable`。
  4. **约束**：`DEPARTMENT` 必填 `dept_id`；`PERSONAL`/`ORG` 忽略。`task.category_id` 只可引用**可见**分类（建/改任务校验 `assertVisible`）。
  5. **默认范围**：全新部署无数据迁移；如未来导入旧分类数据，一律置 `PERSONAL`。
- **范围边界（本期不做）**：部门**子树**共享、跨部门共享、分类级 ACL；避免复杂。`ORG` 即两级共享的上限。
- **关系**：落地于 `01 §3/§8.2/§8.3#10`、`02 §4.2/§4.5/§4.7`、`03 §5.3.4`、`docs/api/openapi.yaml`（`Category.scope/deptId`）、`fixtures/api/categories/*`、`05需求覆盖矩阵.md`。

---

## ADR-016 · 去 PostgreSQL/Redis，改 SQLite + 无缓存层（单机轻量交付）

- **状态**：已确认（2026-09-29 产品决策；**取代 ADR-001 中原 PG/Redis 选型、ADR-011 决策#1（PG 最低 13）**）
- **背景**：系统规模小（约 **100 人/日**、每人 ≤20 条/日，即 ≈**2000 写/日**、库体积几十 MB/年），且明确**不考虑多实例/集群**；目标是「拿到 jar 即跑」的零外部依赖交付。PG + Redis 属功能过剩，徒增交付与运维成本。
- **决策**：
  1. **数据库改 SQLite 3**（嵌入式，`org.xerial:sqlite-jdbc`；文件默认 `./data/bluebird.db`）；**移除 PostgreSQL**。
  2. **移除 Redis 与全部缓存层**（不引入 Caffeine）：量级极小，DB 直读足够。
  3. **类型映射**：`BIGINT→INTEGER`、`VARCHAR(n)→TEXT`、`SMALLINT/BOOLEAN→INTEGER`、`TIMESTAMPTZ→TEXT(ISO-8601)`、`JSONB→TEXT(JSON)`；时间列默认 `(strftime('%Y-%m-%dT%H:%M:%fZ','now'))`。
  4. **连接与并发**：HikariCP **单连接池**（`maximum-pool-size=1`）+ 启动 PRAGMA：`journal_mode=WAL`、`busy_timeout=5000`、`foreign_keys=ON`、`synchronous=NORMAL`。
  5. **原 Redis 用途落点**：refresh token 白名单 → 表 `sys_refresh_token`（单活跃会话，部分唯一索引）；登录失败计数 / 防重复提交 → 进程内 `ConcurrentHashMap`（带 TTL 清理）；org-sync 幂等锁 → 进程内 `ReentrantLock`。
   6. **Schema 版本管理**（**全新库、无数据迁移**）：Flyway 保留，加 `flyway-database-sqlite` 模块，`V1__init.sql` 直接写 SQLite 方言；每次部署均为全新初始化。
  7. **单实例**：SQLite 单写者特性决定**仅支持单实例**；多实例/集群不支持，调度 leader 选举等列二期（不适用）。
  8. **部署**：Docker Compose 仅 `backend` 单服务（挂载 `data/` 与 `files/`）；无 `postgres`/`redis` 服务。
  9. **备份**：`sqlite3 .backup`（或停写复制 `.db`）/ `VACUUM INTO`；指引见 `06 §4`。
- **后果**：
  - 交付极简：单 jar + 单 `.db` 文件，无外部服务；`docker compose up` 仅起一个容器。
  - **放弃多实例/水平扩展**（与「不考虑集群」一致）；**放弃强类型 / JSONB 查询能力**（JSON 存 TEXT，应用侧解析）。
  - 需自行保证 `.db` 文件备份与单实例访问（禁止网络文件系统多挂载）。
- **关系**：落地于 `01 §4.1/§5/§7.5/§7.7/§10/§11.1/§12/§15`、`02 §1.7/§1.8/§1.9/§2.2/§2.3/§2.7/§3.2/§4.2/§5.3/§6.2`、`06 §1/§4`、`deploy/docker-compose.yml`、`backend/README.md`。

---

## ADR-017 主键改为 JS 安全的 53 位雪花变体（**修订「默认雪花 ID」实现口径**）

- **状态**：已确认（2026-09-30，**联调实测发现的阻断级缺陷修复**）
- **背景**：后端原用 MyBatis-Plus 默认雪花生成器（`id-type: assign_id`），产出 id 约 **2.1e18**，**超过 JavaScript 的 `Number.MAX_SAFE_INTEGER`（2^53-1 ≈ 9.007e15）**。浏览器 `JSON.parse` 会**静默改写末位**——实测服务端 `2105180185270796289` 经前端变为 `2105180185270796300`（差 11）。
  - **后果（实测确认）**：所有「按 id 访问」的请求全部落空——点开任务详情返回 10004（面板显示「任务不存在」或停在空态），**无法编辑/完成/删除任务，子任务与附件操作一并失效**。前端大量使用 `Number(task.id)`（`TaskView.vue`、`stores/task.ts`、`BbTaskDetailPanel.vue` 等），**无法在客户端补救**。
  - 该缺陷此前未被发现：前端 E2E 走 MSW fixtures（fixture 用小 id 如 101/1201），从未与真实后端联调。
- **决策**：新增 `IdentifierGenerator` Bean（`config/mybatis/JsSafeIdGenerator`）取代 MP 默认实现：
  ```
  id = ((当前毫秒 - 纪元 2024-01-01T00:00:00Z) << 12) | 毫秒内序列
  ```
  - **41 位毫秒 + 12 位序列 = 恰好 53 位**，最大值 `2^53-1`，即前端可精确表示的整数上限，**前后端均不丢精度**。
  - 保留时钟回拨兜底（≤5s 自旋追平，超出 fail-fast），与本仓 `common.util.IdGenerator`（02 §1.10 R7）同口径。
  - **不保留 workerId**：ADR-016 已明确 SQLite **仅支持单实例**；多实例（集群化）本就必须更换为 C/S 数据库并重新设计 id/锁方案。
  - 纪元 2024-01-01 起可用约 **69 年**（至 ~2093）。
- **后果**：
  - 新 id ≤ 9.007e15，浏览器与 Node 均可精确表示；**前端与 openapi 契约零改动**（id 仍为整数）。
  - **修订** `02后端模块详细设计.md §1.10` 中「41 位毫秒 + 5 位 workerId + 12 位序列」的表述；实现以本 ADR 为准。
  - **已存在的旧库**（2.1e18 量级 id）在界面上仍不可按 id 操作；按 **ADR-007「空库起步、不迁移旧数据」**，正常部署不受影响，但若已用旧 jar 建过库需重建。
  - 多实例仍不可用（与 ADR-016 一致）。
- **关系**：落地于 `backend/src/main/java/com/bbtc/bluebird/config/mybatis/JsSafeIdGenerator.java`；关联 ADR-007（空库起步）、ADR-016（SQLite 单实例）。

---

## ADR-018 用户必须归属部门 + 系统默认部门 + 用户联系方式契约

- **状态**：已确认（2026-09-30，**联调验收反馈 #5/#8 落地**）
- **背景**：验收发现三个问题——
  1. **组织管理没有任何部门**：空库起步（ADR-007）后 `sys_department` 为空，而「新建用户」又需要部门，
     形成「没部门可选 → 建不了用户」的死锁前态；
  2. 用户**可以在没有部门的情况下被创建**，导致组织归属缺失、后续按部门过滤/组织级可见性（ADR-012）失效；
  3. `mobile` 被**一律脱敏**（`138****0000`）返回，管理端拿不到原值就**无法编辑手机号**；且用户没有邮箱字段。
- **决策**：
  1. **系统默认顶级部门**：`sys_department` 增 `is_system`（Flyway `V3__dept_system_flag.sql`），
     由 `DefaultDepartmentRunner` 在启动时保证「存在且仅一个受保护的顶级部门」：
     空库创建「总公司」；旧库把**最早创建的顶级部门**提升为系统部门（幂等，不重复插入）。
     该部门**可改名、不可删除**——`DepartmentService.delete` 依 `is_system` 拒绝，前端也不渲染删除入口。
  2. **新建用户必须选择部门**：`CreateUserCmd.deptId` 加 `@NotNull`（缺失返回 10001），前端表单同步必填校验。
  3. **无部门用户兜底**：`DefaultDepartmentRunner` 随后把 `dept_id IS NULL` 的用户归入默认部门，
     覆盖 OIDC/SCIM 自动建号（自动建号本就不经管理端接口）与历史账号，保证「每个用户都有部门」成立。
     执行顺序：`SeedAdminRunner`（`@Order(10)`）→ `DefaultDepartmentRunner`（`@Order(20)`），
     使首次启动的种子 ADMIN 也在同一轮被归入默认部门。
  4. **联系方式契约**：`UserVO` 增 `email`；`POST /users`、`PUT /users/{id}` 增可选 `mobile`/`email`
     （**传空串表示清空**）。`mobile` 脱敏口径改为：**ADMIN / USER_MANAGER 与本人返回明文**，其余角色仍脱敏——
     原「一律脱敏」使管理端无法编辑（用户反馈 #8）。
- **后果**：
  - 组织管理开箱即用；「每个用户都有部门」成为系统不变量；管理端可维护手机号与邮箱。
  - 升级旧库时会自动补一个系统默认部门，并**把无部门用户改挂到该部门**（一次性、幂等，日志可见）。
  - `PUT /users/{id}` 的 `mobile`/`email` 清空语义依赖显式 `UpdateWrapper.set`：
    MyBatis-Plus `updateById` 默认忽略 null 字段，仅靠实体赋值无法清空。
  - 角色仍为固定四值枚举（ADR-003），**不引入角色表/角色管理界面**；如需自定义角色属二期并需新 ADR。
- **关系**：落地于 `V3__dept_system_flag.sql`、`DefaultDepartmentRunner`、`SeedAdminRunner`、`DepartmentService`、
  `Department`/`DepartmentVO`、`CreateUserCmd`/`UserUpdateReq`/`UserDTO`、`UserService`、
  `docs/api/openapi.yaml`（`UserVO.email`、`UserCreateReq`/`UserUpdateReq`、`Department.system`）、
  `frontend/src/views/admin/OrgManage.vue`；关联 ADR-003（角色固定）、ADR-007（空库起步）、ADR-012（组织级可见性）。

---

> 关联文档：`01蓝鸟重构方案.md`（§1.3/§3/§4.2/§5/§7.5/§7.6/§7.7/§7.9/§8.0/§8.2/§8.2.1/§8.3/§9/§11/§12/§13/§14/§15）、`02后端模块详细设计.md`（§1.7/§1.7.1/§1.8/§1.8.1/§2/§3/§4.2/§5/§6）、`03前端模块详细设计.md`（§2 重写质量保障/§3.2/§5 模块设计）、`05需求覆盖矩阵.md`（企业级 TODO 需求 R1–R10 覆盖对照）、`06合规对照表.md`（等保 2.0 三级 + PIPL 对照）、`.gitignore`（`backend/src/main/resources/static/`）。
