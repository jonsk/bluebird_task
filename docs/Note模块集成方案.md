# Note 模块集成方案

> 状态：基于 Memos v0.31.0 功能 + BlueBird Task 现有架构
>
> 接口 / 类型 / 组件契约以本文件为唯一事实源。

## 1. 背景与目标

将 [Memos](https://github.com/usememos/memos)（开源自托管备忘录）的核心能力以 Java 重写后合并到 BlueBird Task 系统，作为 `modules/note` 模块。

> **产品定位：note 是个人工作日志（work log），不是私人日记/想法。** UI 统一称"工作日志"。

- **后端**：Java 21 + Spring Boot 3 + MyBatis-Plus + SQLite，与 BlueBird 完全一致。
- **前端**：Vue 3 + Element Plus + Tailwind，复用 BlueBird 前端，新增 note 页面。
- **标签**：task 与 note **共用同一张 `tag` 表**，关联表各自独立（`task_tag` 已有、`note_tag` 新建），跨实体统计用只读视图 `v_tag_relation`。
- **编辑器**：纯文本输入框，不做 markdown。
- **集成点**：note 可引用 task（`note_relation` 支持 `target_type=TASK`）。
- **访问控制**：企业级应用，**不做公开免登录分享**。owner 可把**所有**工作日志的查看/编辑权限授予指定用户（用户级全量授权）。
- **日志日期**：用独立 `log_date` 字段（工作日），日历/列表按此聚合。
- **部署**：**全新部署**，V6 只建表；做一次空库 V1→V6 迁移冒烟。

### 不做的事

| 不做                         | 原因                                                                                                                              |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Markdown 编辑 / 渲染           | 用户决定：纯文本输入框                                                                                                                     |
| 重写 Memos markdown 解析器      | 不需要 markdown                                                                                                                    |
| 重写 Memos CEL 查询引擎          | 用 MyBatis-Plus 条件构造器                                                                                                            |
| Space 空间隔离                 | BlueBird 用 org/department 替代                                                                                                    |
| 认证 / IdP / SSO             | 复用 `modules/identity`                                                                                                           |
| 文件存储                       | 复用 `modules/file`，须补附件访问控制（**实体无关**的 canBind/canReadGeneric/canDelete + `AttachmentReferenceGuard` 引用保护）、引用计数、deleteByTask 解绑保护 |
| 审计日志                       | 复用 `modules/audit`（仅审计，非收件箱）                                                                                                    |
| MCP / AI 集成                | 缓做                                                                                                                              |
| 链接预览（URL 卡片）               | 依赖 markdown，砍掉                                                                                                                  |
| 重构 task_tag → tag_relation | 破坏面过大，改用 `note_tag` + 视图                                                                                                        |
| 公开免登录分享                    | 改为权限控制（`note_user_grant` + 下属可见）                                                                                                |
| 逐条 note 授权                 | 用户级全量授权                                                                                                                         |
| 站内收件箱 / 红点通知               | 本期不做；代授权通知走审计 + 授权页可见                                                                                                           |

## 2. 功能范围

### 第一层：核心（本期交付）

| 功能           | 说明                                            |
| ------------ | --------------------------------------------- |
| Note CRUD    | 创建 / 编辑 / 删除 / 列表 / 详情                        |
| 纯文本输入        | 漂亮输入框，存纯文本                                    |
| 日志日期         | `log_date` 字段                                 |
| 标签           | 手动选择，共用 `tag` 表 + `note_tag`                  |
| 可见性          | PRIVATE / INTERNAL（本部门含子树）/ PUBLIC            |
| 用户级授权        | owner 把**所有**日志权限授予指定用户（VIEWER/EDITOR），三种发起方式 |
| 下属可见         | 复用 `manager-can-read-subordinate` 开关          |
| 置顶 / 归档 / 删除 | 见 §3.4                                        |
| 附件           | 关联 attachment，补访问控制                           |

### 第二层：特色（本期交付）

| 功能         | 说明                               |
| ---------- | -------------------------------- |
| 日历视图       | 按 `log_date` 聚合                  |
| 探索页        | 浏览 PUBLIC 日志                     |
| 引用         | note 互引（REFERENCE）+ note 引用 task |
| 授权管理页      | 发起/确认/撤销，查看授权列表                  |
| ADMIN 全量日志 | `scope=all`（仅 ADMIN）             |

### 第三层：缓做 / 不做

| 功能                          | 原因           |
| --------------------------- | ------------ |
| 评论 / emoji / 地图 / 收件箱 / 回收站 | 二期或缓做（见 §13） |
| task 详情展示关联 note            | 二期           |

## 3. 数据模型

### 3.1 表结构（5 张表 + 1 视图）

遵循 BlueBird SQLite 约定（ADR-016）。

```sql
-- V6__note.sql（全新部署，5 张表 + 1 视图）

-- ============ note（工作日志） ============
CREATE TABLE note (
    id          INTEGER PRIMARY KEY,
    content     TEXT    NOT NULL,                   -- 纯文本，上限 20000（Service 校验）
    log_date    TEXT    NOT NULL,                   -- 工作日 YYYY-MM-DD
    visibility  TEXT    NOT NULL DEFAULT 'PRIVATE', -- PRIVATE | INTERNAL | PUBLIC
    pinned      INTEGER NOT NULL DEFAULT 0,
    archived    INTEGER NOT NULL DEFAULT 0,
    owner_id    INTEGER NOT NULL,
    dept_id     INTEGER,                            -- owner 部门快照（INTERNAL 用，语义见 §6.2）
    version     INTEGER NOT NULL DEFAULT 0,         -- 乐观锁（实体 @Version）
    deleted     INTEGER NOT NULL DEFAULT 0,         -- 逻辑删除，不可恢复
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by  INTEGER,
    updated_at  TEXT,
    updated_by  INTEGER
);
CREATE INDEX idx_note_owner_list ON note(owner_id, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0;
CREATE INDEX idx_note_explore    ON note(visibility, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0 AND archived=0;
CREATE INDEX idx_note_internal   ON note(dept_id, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0 AND archived=0;
CREATE INDEX idx_note_logdate    ON note(owner_id, log_date) WHERE deleted=0 AND archived=0;
-- 不再建 idx_note_owner_date（与 idx_note_logdate 前缀重复）

-- ============ note_tag ============
CREATE TABLE note_tag (
    id      INTEGER PRIMARY KEY,
    note_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL,
    UNIQUE (note_id, tag_id)
);
CREATE INDEX idx_nt_note ON note_tag(note_id);
CREATE INDEX idx_nt_tag  ON note_tag(tag_id);

-- ============ note_user_grant（用户级全量授权） ============
CREATE TABLE note_user_grant (
    id          INTEGER PRIMARY KEY,
    grantor_id  INTEGER NOT NULL,               -- 授权人（日志 owner）
    grantee_id  INTEGER NOT NULL,               -- 被授权人
    role        TEXT    NOT NULL,               -- VIEWER | EDITOR
    status      TEXT    NOT NULL DEFAULT 'PENDING', -- PENDING|APPROVED|REJECTED|REVOKED
    mode        TEXT    NOT NULL DEFAULT 'OWNER',   -- OWNER | SUPERIOR | ADMIN
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by  INTEGER,
    approved_at TEXT,
    approved_by INTEGER,
    revoked_at  TEXT,
    revoked_by  INTEGER
);
CREATE UNIQUE INDEX uq_nug_active ON note_user_grant(grantor_id, grantee_id)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_nug_grantor ON note_user_grant(grantor_id, status);
CREATE INDEX idx_nug_grantee ON note_user_grant(grantee_id, status);
CREATE INDEX idx_nug_created ON note_user_grant(created_by, status);

-- ============ note_attachment_ref ============
CREATE TABLE note_attachment_ref (
    id            INTEGER PRIMARY KEY,
    note_id       INTEGER NOT NULL,
    attachment_id INTEGER NOT NULL,
    sort          INTEGER,
    UNIQUE (note_id, attachment_id)
);
CREATE INDEX idx_nar_note ON note_attachment_ref(note_id);
CREATE INDEX idx_nar_att  ON note_attachment_ref(attachment_id);

-- ============ note_relation（仅 REFERENCE） ============
CREATE TABLE note_relation (
    id              INTEGER PRIMARY KEY,
    source_note_id  INTEGER NOT NULL,
    target_type     TEXT    NOT NULL DEFAULT 'NOTE', -- NOTE | TASK
    target_id       INTEGER NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), -- 引用时间（非 note 创建时间，可晚于 note）
    created_by      INTEGER,                                                          -- 引用人（EDITOR/ADMIN 也能添加，非必为 source owner；为空时 UI 显示"—"）
    UNIQUE (source_note_id, target_type, target_id)
);
CREATE INDEX idx_nr_source ON note_relation(source_note_id);
CREATE INDEX idx_nr_target ON note_relation(target_type, target_id);

-- ============ tag(name) 索引 ============
CREATE INDEX idx_tag_name ON tag(name);

-- ============ v_tag_relation（跨实体标签统计视图） ============
CREATE VIEW v_tag_relation AS
SELECT 'TASK' AS target_type, tt.task_id AS target_id, tt.tag_id
FROM task_tag tt
JOIN task t ON t.id = tt.task_id
WHERE t.deleted = 0
UNION ALL
SELECT 'NOTE', nt.note_id, nt.tag_id
FROM note_tag nt
JOIN note n ON n.id = nt.note_id
WHERE n.deleted = 0;
```

**CHECK 约束说明**：枚举（`visibility/status/role/target_type/mode`）不在 SQL 加 CHECK（与现有 `task` 表一致），由 Service 层保证 + 单元校验测试（写入 ADR）。

### 3.2 标签统一方案

task 与 note 共用 `tag` 表（`UNIQUE(owner_id, name)`），关联表各自独立。

**关键规则**：

1. `tag` 表不变，`task_tag` 不动。
2. 跨实体统计用 `v_tag_relation`（过滤 note/task 的 deleted）。
3. **标签归属校验**：note 创建/更新时每个 `tag_id` 必须属于 `note.owner_id` 的标签。
4. **跨用户场景一律按 `tag.name` 过滤**（shared/explore/internal/subordinate/all/visible）：当前用户的 `tag_id` 与目标 note 的 `tag_id` 不同，按 `tag.name`。**仅 `mine` 可用 `tag_id` 或 `name`**。
5. **标签 facet 端点**：非 mine scope 的标签筛选需要目标 note 的标签候选，当前用户标签表里没有别人 note 的标签名。新增 **`GET /api/v1/notes/tags?scope=shared|explore|internal|subordinate|all|mine`**：
   - **响应 schema**：
     ```ts
     // 统一返回数组（非 {scope,items} 可辨识联合）：
     // - scope=mine：返回当前用户标签（含 id，兼容 tagId 过滤）
     // - scope=非 mine：返回目标 note 的标签名+使用次数（无 id，按 tagName 过滤）
     type NoteTagFacetItem = { id?: number; name: string; count: number };
     type NoteTagFacet = NoteTagFacetItem[];  // 直接数组，非 {scope, items}
     ```
   - **聚合过滤**：facet **复用 `NoteVisibilitySqlBuilder`/scope 条件**（不直接用 `v_tag_relation`——该视图只过滤 `deleted=0`，不含归档/权限过滤），**固定 `archived=0`**（ADMIN `scope=all` 除外，可含归档）；只返回有 note 的标签（`count>0`）；按 `count DESC, name ASC` 排序。Service 实现必须调用 `NoteVisibilitySqlBuilder` 构建与列表查询相同的可见性条件，否则会泄露"他人有多少条某标签日志"。
   - **权限**：`scope=mine` 任何登录用户可调；`scope=subordinate` 受 `managerCanReadSubordinate` 开关（关闭→HTTP 403）；`scope=all` 仅 ADMIN；其余 scope 登录用户可调但只返回当前用户可见的 note 的标签。
   - `NoteFilterBar` 增加 `tagOptions?: Array<{ id?: number; name: string; count?: number }>` prop，由页面按 scope 调用此端点填充；mine 用 id、其他 scope 用 name。OpenAPI 同步 schema（§10.4）、mock 与验收矩阵（§11.2 A-L）。
6. **删除标签清理两个表——用共享清理器**：新增**公共接口 `TagUsageCleaner`** + **`List<TagUsageCleaner>` 注入**（Spring 自动装配多实现）。`TagService.delete()` 依次调用全部 `TagUsageCleaner`。`TaskTagCleaner`（清 task_tag，task 模块）与 `NoteTagCleaner`（清 note_tag，note 模块）各自实现该接口注册。**不引入领域事件**。接口定义在公共包，避免 task↔note 模块互相依赖。
   - **接口方法契约**：`void cleanByTagId(Long tagId)`；**调用方事务**：由 `TagService.delete()` 的同一事务包裹（先校验 tag 存在与归属 → 调全部 `cleanByTagId` → 最后 `delete tag` 行），任一清理失败整体回滚。写入接口 Javadoc。
7. **批量装配**：列表查标签一次 `IN (note_ids)` 批量查。
8. **ADMIN 编辑他人日志**：`NoteDetailVO` 携带 owner 的标签列表，前端据此回填。


**v_tag_relation 统计口径**：视图**只做实体存在性过滤**（`deleted=0`），**不含归档/权限过滤**——若 Service 用本视图做标签计数，必须**按当前用户可见范围二次过滤**（沿用 `NoteVisibilitySqlBuilder`/`TaskAuthService`），否则会泄露"他人有多少条某标签日志"。该约定写入视图注释与 ADR。

### 3.3 访问控制模型（INTERNAL 方案 2 + visible 与 isVisible 同源）

**前提**：note 是工作日志。**PRIVATE = 方案 A**、**INTERNAL 方向 = 方案 2**：

> **PRIVATE = 不进 explore/部门列表，但上级在 `manager-can-read-subordinate` 开关开启时仍可读。**
>
> **INTERNAL = `note.dept_id ∈ deptAndDescendants(currentUserDept)`（本级+子级：父看子、子不看父）。**

**INTERNAL 部门判定——单一事实来源**：

> 由于 SQL 无法逐行调用 Java 方法，"isVisible 与列表共用同一函数"必须有可落地的载体，定为：**共享 `DepartmentPolicy.visibleDeptIds(me)` 集合**——
>
> - SQL 侧：`scope=internal` 用 `note.dept_id IN (visibleDeptIds(me))`；
> - Java 侧：`isVisible` 第 5 步用同一集合 `visibleDeptIds(me).contains(note.dept_id)`。
>
> `visibleDeptIds(me) = deptAndDescendants(deptIdOf(me))`。列表与 `isVisible` **都只能从这一个函数取值**，禁止各写一套。
>
> **部门集规模控制**：组织可能数千部门，单条 SQL 对 `IN (...)` 参数数量有限（SQLite 约 999 个绑定参数）。**唯一推荐实现：把部门树展开下推到数据库——SQLite 递归 CTE** `WITH RECURSIVE dept_tree(id) AS (...) SELECT id FROM dept_tree`，列表/日历用 `note.dept_id IN (SELECT id FROM dept_tree)`——**一条 SQL 内完成部门树展开 + 全局排序 + 分页 + COUNT + 按日计数**，天然一致、无 999 参数问题。**降级路径（仅当部门树需 Java 侧逻辑无法下推时）**：① 单条 OR 分组 `dept_id IN (chunk1) OR dept_id IN (chunk2)...`（一次执行，非多次查询）；或 ② 临时表 `CREATE TEMP TABLE visible_dept(id PRIMARY KEY)` 批量插入后 `JOIN`。**禁止在列表/日历接口做"每次查 chunk 后内存并集"**——会导致错页、漏数、COUNT 重复（`visible` 的 OR 分支与 internal/subordinate 重叠时同一行命中多次）。该上限与降级策略写入 `DepartmentPolicy` Javadoc 与 ADR，并补 >500 部门的分页/排序/总数/日历一致性测试。

**note 可见性判定（NoteAuthService.isVisible）**：

```
isVisible(note, currentUser):
    ├── 0. 若 note.deleted == 1 → false
    ├── 1. isAdmin → true
    ├── 2. note.owner_id == currentUser → true
    ├── 3. 若 note.archived == 1 且非 owner 且非 admin → false
    ├── 4. note_user_grant 存在 (grantor_id=owner, grantee_id=currentUser, status=APPROVED) → true
    ├── 5. visibility=INTERNAL 且 visibleDeptIds(me).contains(note.dept_id) → true
    ├── 6. visibility=PUBLIC → true
    └── 7. managerCanReadSubordinate=true 且 owner ∈ subordinates(me) → true（方案 A，含 PRIVATE）
```

**`scope=visible` 与 `isVisible` 同源**：

> `scope=visible` **不手写独立并集**，而是基于与 `isVisible` 完全一致的判定：列出当前用户**所有已可见且未归档**的 note，精确定义为 **`visible = isVisible(note, me) && note.archived = 0`**。
>
> 落地：后台不通过"mine∪shared∪...∪subordinate 手工拼接 SQL"，而是维护一个**共享可见性条件构建器** `NoteVisibilitySqlBuilder.visiblePredicate(me)`（等价于逐条跑 `isVisible` 的 SQL 表达，含第 7 步的 `managerCanReadSubordinate && owner ∈ subordinates(me)`，并额外叠加 `archived=0`），供 `scope=visible` 使用。**下属分支必须带 `managerCanReadSubordinate` 条件**；`visibleDeptIds(me)` 为空时跳过 internal 分支，禁止 `IN `。Java 侧 `isVisible` 与 SQL 侧 `visiblePredicate` 用 §6.3 的 15 行等值矩阵约束同源。
>
> **开关关闭时 `scope=visible` 行为**：静默省略下属分支（不返回下属 PRIVATE note），**不整体 403**；`scope=subordinate` 开关关闭才返回 403。

**关键规则**：

1. **INTERNAL / visible 同一判定源**：`visibleDeptIds(me)`、`NoteVisibilitySqlBuilder.visiblePredicate(me)` 为唯一事实来源，禁止重复拼条件。
2. **deleted 前置**：`isVisible` 开头 `deleted==1 → false`。
3. **归档提前**：owner/admin 后立即判断 archived，其余角色不能绕过。
4. **dept_id 统一获取**：`CurrentUserDeptService.deptIdOf(userId)`（查 SysUserMapper），禁止 `UserContext.deptId`。无部门时 `scope=internal/visible` 的 internal 部分返回空集（不抛 NPE）。
5. **`dept_id` 刷新语义**：见 §6.2——**仅在 visibility 由非 INTERNAL 转为 INTERNAL 的当下**刷新 `dept_id = deptIdOf(owner)`；已存在 INTERNAL 保持快照。
6. **field-level 权限**（见 §5.1）。
7. **scope 与 visibility 优先级**：`scope` 先生效；`visibility` 仅作 scope 内附加过滤，见 §5.1。
8. **SUPERIOR 授权是显式授权，不受 `managerCanReadSubordinate` 开关影响**：开关只控制"无 grant 时上级对下属日志的**自动可见**"（isVisible 第 7 步）；`mode=SUPERIOR` 是**显式授权**（上级发起、下属确认后 APPROVED），**开关关闭仍可发起、已 APPROVED 的 SUPERIOR grant 仍生效、开关重开历史 grant 继续生效**。授权页文案注明"上级显式授权独立于下属可见开关"。

**list scope**：

| scope         | 语义                                                       |
| ------------- | -------------------------------------------------------- |
| `mine`        | 我创建的（默认）                                                 |
| `shared`      | 被授权给我的（grantee=me 的 grantor 的日志）                         |
| `explore`     | PUBLIC                                                   |
| `internal`    | `visibleDeptIds(me)` 覆盖部门的 INTERNAL 日志（方案 2）             |
| `subordinate` | `owner ∈ subordinates(me)`（开关开时；开关关→403）                 |
| `visible`     | 当前用户所有已可见（含 subordinate，受开关控制）且未归档的 note（与 isVisible 同源） |
| `all`         | 全部日志（仅 ADMIN；支持 `archived=true` 查他人/全部归档日志）              |

### 3.4 归档与删除语义

| 状态  | archived | deleted | 说明                                  |
| --- | -------- | ------- | ----------------------------------- |
| 正常  | 0        | 0       | 主列表展示                               |
| 已归档 | 1        | 0       | 归档区展示，可 unarchive；仅 owner/admin 可读写 |
| 已删除 | 0        | 1       | 逻辑删除不可恢复；事务内清理关联表                   |

**删除级联**：`NoteCommandService.delete()` 同一事务清理：

- `note_tag`、`note_attachment_ref`、`note_relation`（source+target 两侧），再 `UPDATE note SET deleted=1`。

## 4. 模块结构

```
backend/src/main/java/com/bbtc/bluebird/modules/note/
├── domain/
│   ├── Note.java                      # 主实体，@Version + 逻辑删除
│   ├── NoteTag.java
│   ├── NoteUserGrant.java             # + mode
│   ├── NoteAttachmentRef.java
│   ├── NoteRelation.java
│   └── Visibility.java
├── application/
│   ├── NoteCommandService.java        # 创建/更新/删除/归档/置顶 + 字段级权限 + 标签 + 级联清理 + dept_id 刷新
│   ├── NoteQueryService.java          # 列表/详情/日历/统计（7 scope 用 NoteVisibilitySqlBuilder）
│   ├── NoteAuthService.java           # isVisible/isWritable + visibleDeptIds + 下属 + 归档 + deleted
│   ├── NoteUserGrantService.java      # 授权（mode + createdBy 视图 + 自动通过记录 approved_by）
│   ├── NoteRelationService.java       # 引用（backlinks、自引用禁止、TASK 可见性）
│   └── NoteAttachmentService.java     # 关联附件（assertCanBind、引用计数、note 下载校验）
├── controller/
│   ├── NoteController.java
│   ├── NoteUserGrantController.java
│   └── NoteCalendarController.java
├── dto/
│   ├── NoteCreateReq.java
│   ├── NoteUpdateReq.java             # + version（PATCH）
│   ├── NoteQuery.java                 # scope 7 种 + tag + keyword + dateRange + pinned + archived + date + sort
│   ├── NoteVO.java                    # + version + tags(本 note 已选) + canEdit/canManage/grantRole（**不含 ownerTags**，见 §10.4）
│   ├── NoteDetailVO.java              # NoteVO + relations/backlinks/attachments + ownerTags（唯一含 ownerTags 处）
│   ├── NoteBacklinkVO.java            # backlinks 来源（sourceNoteId/sourceSummary/sourceOwner*/createdBy/createdByName，见 §10.4）
│   ├── NoteBrief.java                 # + version
│   ├── NoteRelationVO.java            # relations 目标（id/targetType/targetId/targetSummary/createdAt/createdBy/createdByName，与 §10.4 完全一致）
│   ├── NoteAttachmentVO.java          # note 附件（id=关联id/attachmentId/downloadUrl/canRemoveRef/canDelete/createdBy，见 §10.4）
│   ├── NoteAttachmentBindReq.java     # {attachmentId:number}（请求体，见 §5.4）
│   ├── NoteRelationCreateReq.java     # {targetType:'NOTE'|'TASK', targetId:number}（请求体，见 §5.5）
│   ├── NoteUserGrantReq.java          # mode + role + 目标用户（按 mode 决定哪些由服务端推导）
│   ├── NoteUserGrantVO.java           # + mode + createdBy
│   └── NoteCalendarDay.java
├── infrastructure/
│   ├── NoteMapper.java
│   ├── NoteTagMapper.java
│   ├── NoteUserGrantMapper.java
│   ├── NoteAttachmentRefMapper.java
│   └── NoteRelationMapper.java
└── util/
    └── NoteFilterBuilder.java
```

**公共能力**：

| 新增类                                             | 位置                  | 用途                                                                                                     |
| ----------------------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| `CurrentUserDeptService`                        | 公共/org              | `deptIdOf(userId)`                                                                                     |
| `DepartmentPolicy.visibleDeptIds(me)`           | 公共包                 | INTERNAL 可见部门集合（SQL IN 与 isVisible contains 同源）                                                        |
| `NoteVisibilitySqlBuilder.visiblePredicate(me)` | note 模块             | `scope=visible` 的可见性 SQL 条件（与 isVisible 等价，含下属开关）                                                      |
| `AttachmentAccessService`                       | `modules/file`      | **与实体无关**的基础判定：`canBind` / `canReadGeneric` / `canDelete`（均=创建者/ADMIN，**不含 note/task 可见性与引用保护**）       |
| `AttachmentReferenceGuard` 接口 + `List` 注入       | 公共包 + note/task 两实现 | 附件引用保护：`referencedByNote(id)` / `referencedByTask(id)`（与 `TagUsageCleaner` 同模式，避免 file→note/task 反向依赖） |
| `TagUsageCleaner` 接口 + `List` 注入                | 公共包 + 两实现           | 标签删除清理                                                                                                 |

## 5. API 设计

**全部接口需登录**。遵循 BlueBird 风格（`/api/v1/`、`ApiResult`、`@OperateLog`、`@RepeatSubmit`）。

### 5.1 Note CRUD

| 方法          | 路径                                        | 说明             | 权限                                          |
| ----------- | ----------------------------------------- | -------------- | ------------------------------------------- |
| POST        | `/api/v1/notes`                           | 创建             | 登录                                          |
| PUT         | `/api/v1/notes/{id}`                      | 更新（携带 version） | owner / EDITOR grant / ADMIN                |
| DELETE      | `/api/v1/notes/{id}`                      | 软删除 + 级联       | owner / ADMIN                               |
| GET         | `/api/v1/notes`                           | 列表（scope + 过滤） | 登录                                          |
| GET         | `/api/v1/notes/{id}`                      | 详情             | 按可见性（不可见→404）                               |
| POST/DELETE | `/api/v1/notes/{id}/pin`                  | 置顶/取消          | owner / ADMIN                               |
| POST/DELETE | `/api/v1/notes/{id}/archive` `/unarchive` | 归档/取消          | owner / ADMIN                               |
| GET         | `/api/v1/notes/tags?scope=...`            | 标签 facet       | 登录（scope=all 仅 ADMIN；scope=subordinate 受开关） |


**字段级权限**：

| 字段/操作                  | owner | EDITOR grant | ADMIN |
| ---------------------- | ----- | ------------ | ----- |
| content / log_date     | 写     | 写            | 写     |
| visibility             | 写     | 禁止           | 写     |
| tagIds                 | 写     | 仅 owner      | 写     |
| 附件新增                   | 写     | 可写           | 写     |
| 附件移除                   | 写     | 禁止           | 写     |
| 引用新增                   | 写     | 可写           | 写     |
| 引用移除                   | 写     | 禁止           | 写     |
| pin / archive / delete | 写     | 禁止           | 写     |

**PATCH 语义**：`tagIds=null` 不改；`[]` 清空；`visibility` 省略不变；EDITOR 判定基于"值是否与当前一致"。

**写权限算法**——`NoteAuthService` 明确三档：

| 动作                                                               | owner | EDITOR grant | ADMIN | 归档 note                         | 已删除 note |
| ---------------------------------------------------------------- | ----- | ------------ | ----- | ------------------------------- | -------- |
| `canEdit`                                                        | true  | true（字段受限）   | true  | false（EDITOR）/true（owner/admin） | false    |
| `canManage`（visibility/pin/archive/delete/tagIds/引用移除/附件移除）      | true  | false        | true  | owner/admin true                | false    |
| `isWritable`（= canEdit && !deleted && !(archived && 角色==EDITOR)） | true  | 仅未归档         | true  | EDITOR false；owner/admin true   | false    |

- **EDITOR 可写字段**：`content/log_date`、附件新增、引用新增；**不可写**：visibility、tagIds、附件移除、引用移除、pin/archive/delete。
- **archived 时 EDITOR 一律不可写**（含 content）；owner/admin 仍可读写归档项（unarchive）。
- **deleted 一律不可写**（任何角色）。
- `canEdit`/`canManage` 与 §5.1 字段级权限表一致；`grantRole=EDITOR` 的用户在 `archived=1` 时 `canEdit=false`。

**列表查询参数**：

```
GET /api/v1/notes?
    scope=mine|shared|explore|internal|subordinate|visible|all   (默认 mine; all 仅 ADMIN)
    &tagId=12                // mine scope 用标签 id（数字）
    &tagName=工作            // 其余 scope 用标签 name（字符串；id 与 name 无法由单一参数表达，故拆为 tagId/tagName）
    &keyword=xxx            // content LIKE，转义 % _ \
    &visibility=PRIVATE     // scope 内附加过滤（不匹配则忽略）
    &pinned=true
    &archived=false|true    // true 仅 mine 有意义；且 ADMIN 可配 scope=all&archived=true 查全部归档
    &start=&end=           // log_date 范围（与 date 互斥见下）
    &date=2026-10-20       // 指定某天
    &sort=log_date_desc|log_date_asc|created_desc|updated_desc
    &page=1&size=20        // size 上限 100
```

**scope×visibility 合法性**：

| scope         | 允许的 visibility 附加过滤          | 说明              |
| ------------- | ---------------------------- | --------------- |
| `mine`        | 任意                           | 含 archived=true |
| `shared`      | 任意                           |                 |
| `internal`    | 仅 INTERNAL（超范围忽略）            |                 |
| `subordinate` | 任意（方案 A 含 PRIVATE）           | 开关关→403         |
| `explore`     | 仅 PUBLIC（超范围忽略）              |                 |
| `visible`     | 任意（系统按 isVisible 判定）         | 开关关时省略下属分支      |
| `all`         | 任意（含 archived=true，ADMIN 专用） | 仅 ADMIN         |

**行为**：

- `scope=subordinate` 开关关闭 → **403**（`NOTE_SCOPE_DISABLED`）。
- scope 内传不匹配 `visibility` → **忽略该过滤**，正常返回。
- 非 mine scope 收到 `archived=true` → **忽略 archived**，按 unarchived 返回（**唯一例外**：`scope=all` 且当前用户为 **ADMIN** 时，`archived=true` 生效，可列出他人/全部归档日志）。
- `date` 与 `start/end` 同时出现 → **400**（互斥）。
- `size` 上限 **100**，超过截断。
- `tagId?: number`（mine scope 用）、`tagName?: string`（其余 scope 用）；mine 传 `tagId`、其余 scope 传 `tagName`，后端 DTO 同时接收并按 scope 优先级解析。**不使用 `tag` 单参数**（OpenAPI 单 query 参数只能 string，无法表达 number|string 联合）。

**不可见 404/403**：`GET /notes/{id}`、`GET /notes/{id}/attachments`、`GET /notes/{id}/relations`、**`GET /notes/{id}/backlinks`** 对不可见/不存在/已删除资源**一律返回 404**（不暴露存在性，不返回空数组），OpenAPI 注明。

**下属用户列表端点**：

| 方法  | 路径                                | 说明                                                                                                          | 权限 |
| --- | --------------------------------- | ----------------------------------------------------------------------------------------------------------- | -- |
| GET | `/api/v1/users?scope=subordinate` | 返回当前用户的下属用户列表（`PageResult<UserVO>`），供 `BbUserPicker scope='subordinate'` 与 `NoteGrantForm mode=SUPERIOR` 使用 | 登录 |

- **响应 schema**：`ApiResult<PageResult<UserVO>>`（`{list: UserVO[], total, page, size}`），仅返回当前用户直接下属（非递归）。
- **权限**：任何登录用户可调；`managerCanReadSubordinate=false`（开关关闭）时返回**空列表 `[]`**（非 403——该端点是用户选择器数据源，空列表表示"无下属可选"，403 会破坏表单初始化；scope=subordinate 的 403 只针对 note 列表/日历查询）。
- **OpenAPI**：纳入 `openapi.yaml` 与 mock 36 端点清单（非新增端点计数——复用现有 `GET /users` 加 `scope` query 参数，不新增 path）。


### 5.2 用户级授权

| 方法     | 路径                                 | 说明                                                                                                                         | 权限                                    |
| ------ | ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| POST   | `/api/v1/note-grants`              | 发起授权（body 含 mode；三 mode 均显式写 `created_by=发起人`）                                                                             | 登录                                    |
| GET    | `/api/v1/note-grants`              | 列表（`tab=created\|granted\|incoming\|all` 意图参数 + status/role/mode 过滤，`direction` 仅行内展示；转义 `\|` + 统一 4 列）                    | 登录（`tab=all` 仅 ADMIN）                 |
| POST   | `/api/v1/note-grants/{id}/approve` | PENDING→APPROVED                                                                                                           | **grantor==me**（SUPERIOR 行的下属 A）      |
| POST   | `/api/v1/note-grants/{id}/reject`  | PENDING→REJECTED                                                                                                           | **grantor==me**                       |
| POST   | `/api/v1/note-grants/{id}/upgrade` | VIEWER→EDITOR                                                                                                              | **grantor==me**（仅 APPROVED）           |
| DELETE | `/api/v1/note-grants/{id}`         | 撤销（→REVOKED）：**撤 PENDING = `grantor==me OR created_by==me` 或 ADMIN；撤 APPROVED = `grantor==me` 或 ADMIN**（发起人可撤回自己的 PENDING） | grantor / created_by(PENDING) / ADMIN |

**发起请求体（mode 区分）**：

```json
// OWNER（owner 主动）
{ "mode": "OWNER", "granteeId": 5, "role": "VIEWER" }
// SUPERIOR（上级发起，看下属）
{ "mode": "SUPERIOR", "grantorId": 8, "role": "VIEWER" }
// ADMIN（代授权）
{ "mode": "ADMIN", "grantorId": 8, "granteeId": 5, "role": "VIEWER" }
```

**mode 必填与冲突校验**：

- `OWNER`：必填 `granteeId/role`，`grantorId` 必须为空（服务端强制 grantor=me）；`created_by=me`。
- `SUPERIOR`：必填 `grantorId/role`，grantee 服务端推导=me，校验 `subordinates(me).contains(grantorId)`；**body 若携带 `granteeId` → 400（`NOTE_GRANT_MODE_INVALID`）**，该模式 grantee 恒由服务端推导，禁止客户端指定；**`created_by=me`（发起人=上级），`grantor_id=body`（下属），弥合"发起人≠grantor"**。
- `ADMIN`：必填 `grantorId/granteeId/role`，仅 ADMIN，`created_by=me`。
- mode 缺失/未知→400；mode 与角色不符→403。**模式×字段组合校验收敛为显式校验表（请求体 3 模式 × 字段），写入 OpenAPI 与单测。服务端推导 + 自动通过记录**：

| mode     | grantor  | grantee | status   | created_by        | approved_at/by                                                     |
| -------- | -------- | ------- | -------- | ----------------- | ------------------------------------------------------------------ |
| OWNER    | me       | body    | APPROVED | **me（发起人）**       | **approved_by=me, approved_at=now**                                |
| SUPERIOR | body(下属) | me(上级)  | PENDING  | **me（上级，发起人）**    | **下属 grantor 确认后 → APPROVED；approved_by=grantor, approved_at=now** |
| ADMIN    | body     | body    | APPROVED | **me（ADMIN，发起人）** | **approved_by=ADMIN, approved_at=now**                             |

> OWNER/ADMIN 自动通过、SUPERIOR 由 grantor 确认后通过，**均写 `approved_by/approved_at`**；撤销一律写 `revoked_by/revoked_at`（撤销者），补 `SUPERIOR` 的 revoked_by 审计。
>
> **SUPERIOR 与下属开关的关系**：`mode=SUPERIOR` 是**显式授权**、**不受 `managerCanReadSubordinate` 开关约束**——开关关闭仍可发起 `mode=SUPERIOR`；已 APPROVED 的 SUPERIOR grant 在开关关闭时**仍生效**；开关重开后历史 grant 继续生效。开关只控制 isVisible 第 7 步的"无 grant 自动可见"（见 §3.3 关键规则 8）。

**校验**：不能给自己授权；`@RepeatSubmit` + 每 grantor 最多 5 条 PENDING；grantor/grantee 均 ACTIVE。**拒绝/撤销形成独立的 REJECTED/REVOKED 历史行（`uq_nug_active` 部分索引只约束 PENDING/APPROVED，允许多历史行并存）；再次发起一律创建新 PENDING 行，不复用历史行、不清空历史 approved/revoked 字段**（保留完整审计链，避免"复用哪一行"歧义）。

**动作权限**：

- `approve/reject`：**`grantor==me`**（SUPERIOR 行的下属 A，在 `tab=granted` 的 PENDING 行操作）；
- `revoke PENDING`：**`grantor==me OR created_by==me`** 或 ADMIN（**发起人 S 可撤回自己发起的 SUPERIOR 申请**）；
- `revoke APPROVED`：`grantor==me` 或 ADMIN（grantor A 经 `tab=granted` 可撤销已批准 SUPERIOR）；
- `upgrade`：`grantor==me`（仅 APPROVED）。

**direction/status/mode/tab**：**把"授权关系（grantor/grantee）"与"发起动作（created_by）"拆成两个维度，不用一个 `grantor` 同时承担**。`direction` 仅**行内展示**（`outgoing`=grantor_id=me、`incoming`=grantee_id=me、`none`=两者皆非，如 ADMIN 代授权行对 ADMIN），**不参与 Tab 过滤**。`tab` 为查询意图参数，**唯一过滤条件以下表为准（各 Tab 内唯一；同一行可跨 Tab 出现，不用"互斥"描述整个关系）**：

| tab                   | 唯一过滤条件                                 | 按钮语义                                                                                                                                                         |
| --------------------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `created`（我发起的）       | `created_by = me`                      | 发起人：OWNER/SUPERIOR/ADMIN 代授权均在此；可撤回自己发起的 PENDING；**APPROVED 行中：`grantor==me`（OWNER 行）可升级/撤销；`isAdmin && created_by==me`（ADMIN 代授权行，grantor=A）可撤销、不可升级；其余只读** |
| `granted`（我授权的， 方案 A） | `grantor_id = me`（**全状态**）             | 我作为授权方可见全部状态（PENDING/APPROVED/REJECTED/REVOKED）；PENDING 行内显"**确认/拒绝/撤回（grantor==me，撤回后进入 REVOKED）**"；APPROVED 可撤销/升级——保证 SUPERIOR 行通过后 grantor A 仍有入口收回      |
| `incoming`（涉及我的）      | `grantee_id = me AND created_by <> me` | 只读/被授权；**加 `created_by<>me` 排除 S 自己发起的 SUPERIOR 行**（不重复）                                                                                                     |
| `all`                 | ADMIN 全量                               | 只读 + 可代撤销                                                                                                                                                    |

**SUPERIOR 行（grantor=A、grantee=S、created_by=S、status=PENDING）**：S 经 `tab=created` 出现且**可撤回自己的 PENDING**（`created_by==me`）；A 经 `tab=granted`（grantor=A）出现，PENDING 时**确认/拒绝/撤回（grantor==me，撤回进入 REVOKED）**、APPROVED 后仍在该 Tab **可撤销/升级**（A 作为唯一非 ADMIN grantor 必须始终有 UI 入口，不能批准后就消失）；S 的 `tab=incoming` **不重复**（incoming 加 `created_by<>me`，S 行 grantor=A 非 S 不在此）。**ADMIN 代授权行（grantor=A、grantee=B、created_by=admin、status=APPROVED）**：ADMIN（发起人）经 `tab=created` 出现并可代撤销；B 经 `tab=incoming` 出现；A 经 `tab=granted`（grantor=A）出现**并可撤销（`grantor==me`）**——SUPERIOR 与 ADMIN 代授权两种场景下 A 都是被授权日志的 owner（grantor），应在"我授权的"里看到并有权收回。**created / granted / incoming 三处各 Tab 内不重不漏（同一行可跨 Tab 出现，例如 OWNER 授权行 `grantor=created_by=me`、`grantee=他人`（不能给自己授权），在 created 与 granted 各出现一次属正常）。状态机**：upgrade 仅 APPROVED；DELETE 撤 PENDING 的权限见上（发起人/grantor/ADMIN）；grantee 本期不能主动放弃；`expires_at` 不加。

**审计**：发起/确认/拒绝/撤销/升级均 `@OperateLog`。

### 5.3 日历

| 方法  | 路径                                                     | 说明                  |
| --- | ------------------------------------------------------ | ------------------- |
| GET | `/api/v1/notes/calendar?year=2026&month=10&scope=mine` | 按月按 log_date 返回每日数量 |

- **scope 与列表共用同一解析**：支持 mine/shared/explore/internal/subordinate/**visible/all**（与列表 7 scope 完全一致）；`scope=subordinate` 开关关闭与列表一致返回 **403**；`scope=all` 非 ADMIN → **403**（与列表一致）；`scope=visible` 开关关闭 → **静默省略下属分支**（同列表，不 403）。**日历 UI 选择器不暴露 `visible`（无独立落地列表页，仅引用选择器/列表查询值使用）**。
- **口径**：一律**限定** `deleted=0 AND archived=0`；`internal` 用 `visibleDeptIds(me)`（空部门同列表处理，禁止 `IN `）；不用 `UserContext.deptId`。
- **时区**：`Asia/Shanghai`。

返回：

```json
{ "code": 0, "data": [ { "date": "2026-10-01", "count": 3 }, ... ] }
```

**点日查看**：`GET /api/v1/notes?date=2026-10-20`（同 scope 口径）。


### 5.4 附件（实体无关判定 + 引用保护 + /files 权限矩阵）

| 方法     | 路径                                                       | 说明                                                                                              | 权限                                                                                      |
| ------ | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| POST   | `/api/v1/notes/{id}/attachments`                         | 关联附件（body `NoteAttachmentBindReq { attachmentId:number }`，单条绑定；多文件上传由前端逐个调 POST） | owner / EDITOR（**须 `NoteAuthService.isWritable`**，归档时 EDITOR 不可写；再校验附件 `assertCanBind`） |
| GET    | `/api/v1/notes/{id}/attachments`                         | 附件列表                                                                                            | 按可见性（不可见404）                                                                            |
| DELETE | `/api/v1/notes/{id}/attachments/{attachmentId}`          | 移除关联                                                                                            | owner / ADMIN                                                                           |
| GET    | `/api/v1/notes/{id}/attachments/{attachmentId}/download` | 下载                                                                                              | note 可见 + 关联存在                                                                          |

**附件权限分离（file 模块仅保留与实体无关的判定；note/task 可见性由各自模块在调用前校验，避免 file→note/task 反向依赖）**：

| 能力               | 判定（file 模块，与实体无关）                        | 用途                                                  |
| ---------------- | ---------------------------------------- | --------------------------------------------------- |
| `canBind`        | **附件创建者本人 / ADMIN**；**不包含"被可见 note 引用"** | bindToTask / note 关联                                |
| `canReadGeneric` | 附件创建者 / ADMIN                            | 仅 `GET /files/{id}`、`GET /files/{id}/preview`（最小权限） |
| `canDelete`      | 附件创建者 / ADMIN（**不含引用保护**）                | `DELETE /files/{id}` 物理删除**前置**判定                   |

> **实体相关判定不移入 file 模块**：
>
> - **note 附件下载**：`NoteAuthService.isVisible(note) && 关联存在`（`NoteAttachmentService` 调用前校验）。
> - **task 附件下载/预览**：`TaskAuthService.isVisible(task) && attachment.task_id == taskId`（`TaskAttachmentController` 校验）。
> - **task 附件删除按钮**：用后端计算的 `canRemoveFromTask`（= `assertWritable && (createdBy==me || isAdmin)`），**不使用 `canDelete`** 直判。
> - **引用保护**：`FileService.delete()` 通过 `List<AttachmentReferenceGuard>`（note/task 各自实现）判断**双引用**；note 关联删除走 `canRemoveRef`；task 附件移除走 `unbindTaskAttachments`。
>
> 明确：**`canBind` 禁止使用"被可见 note 引用"这一条**。上级能读下属附件**不等于**能绑定到自己 note/task（canBind=false）。若产品需要"把别人共享附件转挂到我的日志"，属独立显式转交能力，本期不做。

**入口权限矩阵（最小权限 + task 专用端点）**：

| 入口                                                               | 允许访问                                                       | 用途             |
| ---------------------------------------------------------------- | ---------------------------------------------------------- | -------------- |
| `GET /api/v1/files/{id}`（通用下载）                                   | **仅创建者 / ADMIN**（`canReadGeneric`）                         | 非 note 附件场景    |
| `GET /api/v1/files/{id}/preview`                                 | 创建者 / ADMIN（`canReadGeneric`）                              | 预览             |
| `POST /api/v1/files`（通用上传）                                       | 登录；**仅允许 `taskId=null`**（非空 → 400）                         | note 上传前置 / 通用 |
| `GET /api/v1/notes/{id}/attachments/{id}/download`               | **`isVisible(note)` + 关联存在**                               | note 附件        |
| `GET /api/v1/tasks/{taskId}/attachments/{attachmentId}/download` | **`isVisible(task)` + `attachment.task_id == taskId`** | task 附件下载      |
| `GET /api/v1/tasks/{taskId}/attachments/{attachmentId}/preview`  | **`isVisible(task)` + `attachment.task_id == taskId`** | task 附件预览      |
| `POST /api/v1/tasks/{taskId}/attachments`                        | **task 可写（assertWritable：owner/assignee/ADMIN）**       | task 附件上传      |
| `DELETE /api/v1/tasks/{taskId}/attachments/{attachmentId}`       | **assertWritable + canRemoveFromTask（上传者/ADMIN）**          | task 附件移除/解绑   |
| `GET /api/v1/files/config`            | **登录即可访问**                                                 | 附件上传配置（maxSize/allowedExt） |
> **`GET /files/config` 契约**：响应 `{ code:0, data: { maxSize: number, allowedExt: string[] } }`——`maxSize` 单位为**字节**（如 52428800=50MB），`allowedExt` 空数组=不限制（非空=白名单，如 `['png','jpg','pdf']`，统一无点号 + 大小写归一化）；登录即可访问（不限制角色）；纳入 OpenAPI schema 与 mock 36 端点清单（见 §10.4/§10.5.11）；`useFileConfig()` composable 加载此端点，失败时用 fallback 常量（50MB/空数组）。

> **前端下载鉴权（实现契约）**：浏览器通过 `<a href>` / `window.open` 直连下载 URL **不会携带 `Authorization` 头**，而 `SecurityConfig` 对 `/api/v1/**` 全部 `authenticated()`、JWT 仅从 `Authorization: Bearer` 读取。故**所有附件下载/预览一律走 axios `responseType:'blob'`**。
>
> **blob 错误解析与 token 续签——只保留一个解析点，收敛到响应拦截器**（后端业务异常与认证失败均 `ApiResult.fail(...)` 且 HttpStatus 恒为 `200`，`http.ts` 现有续签只在 **error** 分支读取 `error.response.data.code`，HTTP 200 不会走该分支；且若在 helper 先 `resp.data.text()` 再让拦截器读同一 Blob 会**双重消费读空**）。职责划分：
>
> 1. **`http.ts` 成功拦截器改为 async**：若 `config.responseType==='blob'` 且 `content-type` 含 `application/json`，则 `blob.text()` 读出字符串并尝试 `JSON.parse`：
>    - **判定条件（防合法 JSON 附件被误判为业务错误）**：**不能仅凭 `content-type: application/json` 就当 ApiResult 处理**——用户上传 `.json` 附件时，后端返回的 `Content-Type` 就是 `application/json`，内容是合法 JSON 文件（无 `code` 字段），会被误判为业务错误导致附件无法下载。**判定为 ApiResult 的严格条件（全部满足，原条件 (a) 允许"Content-Disposition 缺失"作为充分条件，但合法 .json 附件可能没有该头，仍会被误判）**：(a) **响应头显式标记 `X-Api-Result: 1`**；(b) `JSON.parse` 成功；(c) 解析结果 `typeof code==='number'` 且 `code` 在已知错误码范围内（1xxxx/2xxxx/5xxxx），**且响应体结构匹配 `ApiResult` schema**（至少含 `code`+`message`+`traceId` 三字段，防用户上传含 `code` 字段的配置文件被误判）。**三者全满足才按 ApiResult 处理，否则原样返回 Blob**（合法 JSON 附件正常下载）。
>    - 满足上述条件且 `code===10002/20005`（token 过期/失效）→ 复用现有 `doRefresh()` 刷新，带 `_retried` 标记**重放原请求**；仍失败 → `redirectToLogin()`；
>    - 其余非 0 code → `throw new BizError(code, message, traceId)`；
>    - `code===0` 不应出现（二进制下载不可能返回业务成功 JSON），若出现按非法响应处理。
>        
>      HTTP 401/403/400/404 走标准 HTTP 状态码、不进入业务码，axios 走 **error** 分支（非 success）。**`HttpError` 类**：现有 `http.ts` 的 `toBizError` 只返回 `BizError`（无 `status` 字段），但页面按 `err.status===404`/`err.status===403` 分支（如 `NoteDetailView` 读 `err.status===404` 渲染 notFound、`NoteSubordinateView` 读 `err.status===403` 渲染空态）——`err.status` 永远 `undefined` 导致 403/404 空态全部失效。**修法**：新增 `HttpError extends Error` 类（`{ status: number; data?: unknown; message: string; traceId?: string }`），错误拦截器对 HTTP 401/403/400/404 等**协议级错误**按 HTTP status 抛 `HttpError`（非 `BizError`）；业务码错误（HTTP 200 + code≠0）仍抛 `BizError`。`NoteDetailView` 等页面统一读 `err.status===404`（`HttpError.status`），**不再读 `err.response.status`**（axios error 已被拦截器转换，`err.response` 不保证存在）。**401 刷新条件**：账号禁用/权限拒绝/网关错误不应触发刷新（否则可能循环或踢回登录）；仅在 HTTP 401 **且** `code` 为 `10002`（token 过期）或 `20005`（token 失效）时才触发 `doRefresh()`，其余 401（如账号禁用 `10003`）直接 `redirectToLogin()` 不刷新。**普通 JSON 10002 统一处理**：HTTP 200 + code 10002 的普通 JSON 同样触发续签——成功拦截器统一处理 10002/20005——无论 `responseType` 是 `json` 还是 `blob`，只要 `code===10002/20005` 都复用 `doRefresh()` + `_retried` 重放。**错误拦截器也需处理 Blob**：若 `error.response.config.responseType==='blob'` 且 `error.response.data instanceof Blob`，先 `await data.text` 解析 `ApiResult`，再按 HTTP status 分流：401 且 code∈{10002,20005} → `doRefresh()` 刷新重放、401 其他 → `redirectToLogin()`、403 → `throw new HttpError(403, message, traceId)`（保留 message/traceId 便于排查，前端按 `err.status===403` 分支，如 scope=subordinate 空态）、其他 → `throw new HttpError(status, message, traceId)` 或 `throw new BizError(code, message)`。错误拦截器与成功拦截器都要处理。mock 同步：blob 错误用例从"200 JSON 403"改为与真实协议一致（HTTP 403 + JSON body），或明确标注"仅兼容旧网关的 200 JSON 错误"。`<4KB + 可解析 JSON 且含 code 字段`兜底嗅探**只在 `content-type` 缺失或为 `text/plain` 时启用**，且要求 `code` 是已知错误码且响应体匹配 `ApiResult` schema——防某些二进制文件恰好是 4KB 内合法 JSON（如配置文件）被误判。
> 2. **`api/file.ts` 的 `fetchAttachment(url)` 只负责**：`http.get<Blob>(url, { responseType:'blob', timeout:0 })`、前导 `stripApiPrefix()` 兜底（防后端误带 `/api/v1`，配合 `http.baseURL='/api/v1'`）、断言 `resp.data instanceof Blob`、从 `Content-Disposition filename*` 提取文件名。**不再二次 `JSON.parse`**（解析已由①拦截器完成）。`fetchAttachment` 只接受**同源相对路径**——拒绝 `^[a-z]+://`、`//`、`..`、反斜杠；用 `new URL(url, window.location.origin)` 后校验 `origin` 相同；或由前端根据 `noteId/attachmentId` 自己拼路径，**完全不信任服务端 `downloadUrl` 作为请求地址**（只用于展示）。`stripApiPrefix()` 只处理 `/api/v1` 前缀，不阻止 `//evil.com`/`https://`/`javascript:`。
> 3. 保存/预览用 `URL.createObjectURL` + 临时 `<a download>`。**noopener**： `window.open('', '_blank', 'noopener')` 会返回 `null`（noopener 模式），后续无法赋 `location`——预览打不开；**修法**：直接 `window.open(blobUrl)` 打开 blob URL（blob URL 同源、无外部引用风险），或先 `const w = window.open('about:blank')` 再 `w.location = blobUrl`（不传 noopener）；预览窗口关闭时 `beforeunload` revoke。**同源校验**：`openAttachment({url})` 内部复用 `fetchAttachment` 的同一 URL 校验（拒绝 `^[a-z]+://`、`//`、`..`、反斜杠、`javascript:`），只接受同源相对路径；调用方传服务端 `downloadUrl` 时先校验再转 blob URL。**文件名三级回退**：`filename*`（RFC 5987，中文编码）→ `filename`（普通）→ `fileName`（前端兜底字段），非 RFC5987 响应也能得到文件名。**预览 MIME 白名单**：`openAttachment` 接收 `contentType` 参数并在内部强制白名单——仅 `image/png|jpeg|gif|webp`、`application/pdf`、`text/plain` 可内联预览；`text/html`/`image/svg+xml` 一律按下载或转义为纯文本（blob URL 继承应用源，HTML/SVG 中的 `<script>` 可读同源 localStorage）；若必须预览 HTML，使用 `sandbox` iframe（无 `allow-scripts`/`allow-same-origin`）并设 CSP。Office 只下载（fallback）。文件名清洗：对 `/\:*?"<>|` 和控制字符做清洗，防路径穿越/显示欺骗。下载在 `a.click()` 后 `setTimeout(revoke, 1000)`；预览在窗口 `load()`/`beforeunload` 或固定较长延迟（如 60s）后 revoke；组件卸载时兜底清理。三套回收规则（下载 1s、预览 60s、组件卸载）并存时引用计数不清会导致提前 revoke 下载失败；集中到 `attachmentBlobCache` 管理引用计数与卸载清理——`acquire(key)` 返回 blob URL 并 +1 计数、`release(key)` -1 计数、计数归零时 revoke；组件 `onUnmounted` 调 `releaseAll()`。缩略图 Map 缓存设 LRU 上限（50 项/64MB），附件移除时删除对应 key 并 revoke；缩略图 LRU key 加 `updatedAt`/`etag` 防命中旧版本。`attachmentBlobCache.releaseAll()` 必须在以下场景调用——(1) `stores/note.reset` / `auth.logout()` 时调 `releaseAll()` 清所有 blob URL（防切换用户后旧 URL 仍可被同源页面访问）；(2) 路由 `onBeforeRouteLeave` 时调 `releaseAll()` 或组件 `onUnmounted` 调 `releaseAll()`；(3) 预览窗口 `beforeunload` 与固定超时（60s）双保险（防窗口关闭后 URL 泄漏）；补"退出登录后 `attachmentBlobCache.size===0`"单测。
> 4. **e2e（mock 用 `HttpResponse.arrayBuffer` 返回二进制并设 Content-Type）覆盖**：200 正常文件、**HTTP 403 + JSON body**、**HTTP 401 + JSON body（触发 doRefresh）**、200 JSON 10002（刷新成功→按 `_retried` 重放原请求、刷新失败→跳登录）、200 JSON 非续签业务码（如 50001）、中文文件名、**无 Content-Type 的 blob 错误嗅探**。

> **task 专用附件端点落点**：task 专用附件端点**不放 `FileController`**（其类级 `@RequestMapping("/api/v1/files")` 会把方法路径错误拼接成 `/api/v1/files/api/v1/tasks/...`）。新增**独立 `TaskAttachmentController`**，类级 `@RequestMapping("/api/v1/tasks")`，方法路径 `/{taskId}/attachments/{attachmentId}/download` 与 `.../preview`；或在现有 `TaskController`（类级已 `/api/v1/tasks`）加方法。`TaskQueryService` / mock 生成 `downloadUrl` 时使用与 Controller **完全一致**的路径，写入 OpenAPI。
>
> **附件不参与 task PUT 全量替换**：
>
> 1. 新增 task 专用端点（校验见下），`BbAttachmentList` 下载/预览改走 task 专用 URL。
> 2. **附件新增/删除不进入 task 表单保存**：现有 `BbTaskDetailPanel.payloadOf()` 不携带 `fileIds`，本方案**不要求** task PUT 做"全量替换"。task 附件的增删由 `BbAttachmentList` 独立上传/删除完成（与现有模型一致）。
> 3. **`fileIds` 语义明确**：`fileIds == null` 与 `[]` **同义，均视为"不动附件"**（不做全量替换、不触发清空）；非空数组（仅显式"新增"入口调用）→ 只对到该 task 的 fileId 处理。**不提供"显式清空附件"入口**——清空只能通过逐个删除附件完成（走 task 专用删除端点，见第 6 条）。
> 4. **`FileService.bindToTask` 只做增量绑定，不做全量替换**：
>    - 对 fileId：`assertCanBind`（创建者/ADMIN）→ 否则 403；不存在 → 404；已属**其他** task → 409；
>    - **已属于本 task** 的 fileId → **no-op**，不重复校验 canBind（避免 owner 因他人上传附件被 403）；
>    - **不在 task update 中调用 `deleteByTask`**。
> 5. **移除单个 task 附件：task 专用删除端点**：`DELETE /api/v1/tasks/{taskId}/attachments/{attachmentId}`（落 `TaskAttachmentController`）——校验 **`attachment.task_id == taskId` + `TaskAuthService.assertWritable(taskId, me)`（写操作）+ `canRemoveFromTask`（=`assertWritable && (createdBy==me||isAdmin)`，已内含 assertWritable，由后端计算）** 后调用 **`FileService.unbindTaskAttachments(taskId, [id])`**（方法契约见附件规则 2）；**任何人（含已不在 task 内的原上传者）不满足 assertWritable 或 `canRemoveFromTask` → 403**；`BbAttachmentList` 删除 task 附件改走该端点。**不再依赖 `DELETE /files/{id}` 处理 task 附件移除**（`/files/{id}` 保留用于通用/非 task 场景）。**注意**：task 附件删除按钮**统一读 `canRemoveFromTask`**（已含 `assertWritable`），**不使用 `canDelete`**（`canDelete` 仅用于 `DELETE /files/{id}` 通用物理删除前置判定，不含 `assertWritable`，会导致 task 不可写但仍允许删除的越权）。
> 6. **task 附件端点校验**：新增 `GET /api/v1/tasks/{taskId}/attachments/{attachmentId}/download` 与 `.../preview`：
>    - `attachment.task_id == taskId`（不匹配 → 404，不泄露存在性）；
>    - **task 可见性**：`TaskAuthService` 需**新增 `isVisible(Long taskId, Long userId)` 重载**（内部 `require(taskId)` + `isVisible(task, userId)`）；不可见 → **404**。**不得直接复用 `assertVisible`**（其抛 `FORBIDDEN`，需显式映射 404）；
>    - 附件或 task 不存在 / task 已逻辑删除 → 404。
> 7. **`DELETE /files/{id}` 与 `FileVO.canDelete`**：`FileVO.canDelete` **仅表示"当前用户可物理删除该文件"**（`createdBy==me || isAdmin`，**不含引用保护**）；`BbAttachmentList` 的 task 删除按钮**改读 `v-if="file.canRemoveFromTask && !disabled"`**（`canRemoveFromTask` = `assertWritable && (createdBy==me||isAdmin)`，由后端计算），不再单独用 `file.canDelete`（避免 task 附件天然带 task_id 误判）。task 附件删除走第 5 条 task 专用端点；note 关联删除另由 note 级权限控制（见 §10.1 权限表）。
> 8. **补测试**：task owner/assignee/CC/下属各自可见下载；上传者本人/ADMIN；note 附件专用端点；`GET /files/{id}` 仅创建者/ADMIN；404 不泄露存在性；owner 编辑含 assignee 附件的 task 不因 canBind 403；`[A,B] → [A,C]` 只移除 B 不误删 A；有 note 引用的 task 附件删除 → 只解 task_id。
>
> 通用 `/files` 保持最小权限（创建者/ADMIN）；note/task 附件一律走各自专用端点。**所有前端附件调用（note 与 task）必须走专用端点**，写入 OpenAPI 与调用清单。

**附件规则（增量绑定 + 上传鉴权分流 + unbindTaskAttachments 调用链）**：

1. `bindToTask(fileIds, taskId)`：**只处理新增 fileId**——对每个 fileId 调 `assertCanBind(fileId, me)`（创建者/ADMIN）→ 否则 403；不存在 fileId → **404**；已属于**其他** task → **409**（`NOTE_ATTACHMENT_BOUND`）；**已属于本 task → no-op（不重复校验 canBind）**。**`FileService.bindToTask` 不再调 `TaskAuthService.assertWritable`**——目标 task 可写由 `TaskApplicationService` 在调度侧统一校验，避免 file→task 模块依赖倒挂，两处不重复校验。
2. **`fileIds` 语义**：`null` 与 `[]` 同义，均**不动附件**；非空=增量新增。**task PUT 不做全量替换**，现有 `BbTaskDetailPanel.payloadOf()` 不携带 fileIds 即可正常工作。**移除单个 task 附件走 task 专用删除端点 `DELETE /tasks/{taskId}/attachments/{id}`**，内部调用 `unbindTaskAttachments`；**绝不调用 `deleteByTask(taskId)`**（会清空整个 task 附件）。

   **`FileService.unbindTaskAttachments` 方法契约**：
   ```text
   @Transactional
   FileService.unbindTaskAttachments(Long taskId, Collection<Long> attachmentIds)
     - 仅处理 attachment.task_id == taskId 的行；
     - 不匹配的 id → 404（TASK_ATTACHMENT_NOT_LINKED，前端文案"附件不属于该任务"，不用 note 错误码 NOTE_ATTACHMENT_NOT_LINKED）；
     - 有 note 引用（经 `AttachmentReferenceGuard`）→ set(task_id, null)，保留文件；
     - 无 note 引用 → 物理删行 + 删盘；
     - 由 TaskAttachmentController 的 task 专用删除端点调用（端点侧已 assertWritable + canRemoveFromTask，方法内不再查权限）；
     - 磁盘物理删除失败：记录日志并提示（磁盘删失败不回滚已删的行，避免隐藏孤儿；由 FieldCleanupJob 周期收尾）。
   ```
   > 本方法有**真实调用链**：仅被 `DELETE /tasks/{taskId}/attachments/{id}`（task 删除元动作）调用；**不在 `TaskApplicationService.update` 中触发**（update 不做全量替换，见规则 1）。`TaskAttachmentController` 删除端点补 `@OperateLog` 删除审计。
3. `POST /notes/{id}/attachments`：先 **`NoteAuthService.isWritable(note)`**（归档时 EDITOR 不可写），再调 **`assertCanBind`**（创建者/ADMIN）；**不能只看可见性**。
4. note 附件上传 `task_id=null`，仅经 `note_attachment_ref` 引用。
5. **task 附件上传统一走专用端点（修复越权 + 依赖倒挂）**：新增 **`POST /api/v1/tasks/{taskId}/attachments`**（落 `TaskAttachmentController`）→ `TaskAuthService.assertWritable(taskId, me)` → `FileService.upload(file, taskId)`。**`assertWritable` 放在 Controller/Task 侧，`FileService.upload` 保持只落盘、不依赖 `TaskAuthService`**（避免 file→task 模块依赖倒挂）。

   **通用 `POST /api/v1/files` 不再接受 `taskId`**：`taskId` 非空 → **400**（`TASK_ATTACHMENT_FORBIDDEN`，阻止把文件挂到不可见/不可写的 task），只允许 `taskId=null`（note 附件上传前置）。

   **前端**：`BbAttachmentList` 上传改走 `uploadTaskFile(taskId, file, opts?)` → task 专用上传端点；note 附件仍走 `uploadFile(file, opts?)`（taskId=null）→ `POST /notes/{id}/attachments`（签名以 §10.5.7 为准）。

   补测试：task owner/assignee 上传成功；CC/无关/不可见用户上传 → 403；note 附件上传 taskId=null → 成功；通用 `/files` 携带 taskId → 400。
6. **`FileService.delete()`（`DELETE /files/{id}` 通用入口）**：先 `canDelete`（创建者/ADMIN），再通过 **`List<AttachmentReferenceGuard>`**（`referencedByNote` / `referencedByTask`，note/task 模块各自实现）做**双引用保护**、**任一被引用 → 禁止物理删除**（提示先解除关联）。**职责分工**：task 附件的移除走 task 专用删除端点 → `unbindTaskAttachments`（有 note 引用自动只解 task_id，不报错）；`/files/{id}` 保留给通用/非 task/物理删除场景，被 note/task 引用时拒绝并提示。两者不混用。
7. **悬挂 task 引用清理**：`FieldCleanupJob` 用**显式 SQL `@Select("select id, deleted from task where id=...")` 查 task 的 `id,deleted`**（**不依赖 `@TableLogic` 的默认 `deleted=0` 过滤**，否则逻辑删除的 task 读成 null、无法区分"真不存在"与"已逻辑删除"）。对 `task_id IS NOT NULL` 且（task 行不存在 **或** `deleted=1`）的 attachment：无 note 引用（经 guard）→ 物理删行+删盘；有 note 引用 → 只解 `task_id`（`set(taskId,null)`）。
8. `deleteByTask(taskId)`（task 删除时整体调用）：有 note 引用（经 guard）→ 只解 `task_id`；无引用 → 物理删行+删盘。
9. 孤儿清理：`FieldCleanupJob` 新增"扫 attachment 行 + `task_id IS NULL` + 无 note 引用（经 guard）+ created<now-24h → 删行+删盘"；旧磁盘清理统一加 24h 阈值。
10. 补测试：上级能读下属附件但不能绑定（403）；用他人 attachmentId 关联自己 note → 403/404；新的 fileId 已属本 task → no-op；附件已被其他 task 绑定 → 409；task 删除后附件解绑清理。

### 5.5 引用

| 方法     | 路径                                          | 说明                                                                                             | 权限                                                |
| ------ | ------------------------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| POST   | `/api/v1/notes/{id}/relations`              | 添加引用（body `NoteRelationCreateReq { targetType:'NOTE' \| 'TASK', targetId:number }`） | owner / EDITOR（**须 `isWritable`**，归档时 EDITOR 不可写） |
| GET    | `/api/v1/notes/{id}/relations`              | 我引用了谁                                                                                          | 按可见性                                              |
| GET    | `/api/v1/notes/{id}/backlinks`              | 谁引用了我（过滤不可见）                                                                                   | 按可见性                                              |
| DELETE | `/api/v1/notes/{id}/relations/{relationId}` | 移除引用                                                                                           | owner / ADMIN                                     |

**引用规则**：

- 仅 REFERENCE；target_type NOTE/TASK；禁止自引用。
- **请求体**：`NoteRelationCreateReq { targetType:'NOTE'|'TASK', targetId:number }`（`targetType` 枚举非法→400）、`NoteAttachmentBindReq { attachmentId:number }`（关联附件用，删除/下载传 `attachmentId`）。校验：附件存在且 `assertCanBind`、target 存在且当前用户可见（task 走 `isVisible`，note 走 `isVisible`/可见性过滤）、自引用→**400**（参数校验错误：自引用是请求参数错误而非目标不存在）。
- 添加时先 **`isWritable(note)`**（归档时 EDITOR 不可写），再校验目标存在且当前用户可见。
- **添加幂等**：`note_relation` 有 `UNIQUE(source_note_id, target_type, target_id)`，重复添加（冲突）→ **返回现有 `NoteRelationVO`，不 409**，与附件关联幂等口径一致。
- **读时 TASK 目标重新校验**（走 `TaskAuthService.isVisible(task, me)`，任务不可见 → 404）；不可见时**统一隐藏该条**（不显示占位，避免泄露 task 存在性，写入 DTO 语义）。
- NOTE 目标过滤 deleted/archived/invisible。
- 删除级联：note 删除清 source+target；task 删除时 TASK 引用悬空，读取过滤不存在 task。
- backlinks 过滤引用者不可见；**`GET /notes/{id}/backlinks` 对不可见/不存在/已删除 note 一律 404**（与 §5.1 统一，不返回空数组泄露存在性）。
- **引用选择器搜索（note 与 task 分流）**：引用 **note** 目标用 `scope=visible`（与列表/详情同一可见性判定）；引用 **task** 目标**新增轻量端点 `GET /api/v1/tasks/search?keyword=&page=&size=`**（**只返回当前用户 `isVisible` 的任务**）。**任务接口没有 `visible` scope**（`TaskQueryService` 仅 day/joined/assigned/collect/all，`scope=all` 语义 ≠ `isVisible`），故 task 引用选择器不走 `/tasks` 列表，而走专用搜索端点（`isVisible` 兜底过滤）。

  **`GET /tasks/search` 响应结构（按 `TaskListData` 风格写 OpenAPI）**：
  ```text
  { code: 0, data: { list: [ { id, title, status, dueAt } ], total, page, size } }   // PageResult 风格，非裸数组
  status   // 与任务模块 status 枚举一致（TODO/DOING/DONE...），由 OpenAPI task schema 复用，前端不写死
  keyword  // 匹配 title（本期仅标题前缀/包含，不匹配内容）；大小写不敏感、trim 空白
  ```
  语义：空关键词（`keyword<2`）→ 返回空 `list`（不 400，方便选择器空态）；已删除任务不返回；已完成任务**返回但可标记**（由选择器是否允许引用已办 task 决定，默认允许引用并正常展示）；结果**不再过滤 archived**（task 无归档概念）。
- **引用选择器交互**：输入 ≥2 字符、300ms 防抖、最多 20 条、滚动加载；已引用项置灰并显示"已引用"；NOTE/TASK 两个 Tab，TASK 结果展示标题+状态+截止日；添加返回 404（目标已不可见/已删除）→ 提示"目标已失效"并刷新列表。

## 6. 核心流程

### 6.1 创建 Note

```
POST /api/v1/notes  { content, logDate, visibility, tagIds }
    ├── 校验 content / log_date / tagIds 归属 owner
    │   ├── log_date 格式：Spring @DateTimeFormat(iso=ISO.DATE) 严格解析，不可解析（如 "abc"/"2026-02-30"/"2026-13-15"）→ 400（绑定阶段，不进 Service）
    │   ├── log_date 业务规则：未来日期最多超今天 7 天（如今天是 10-22，则 log_date ≤ 10-29），超出 → 50012 NOTE_LOG_DATE_INVALID
    │   └── tagIds 归属：每个 tagId 须属于创建者本人（非他人标签），否则 → 50003 NOTE_TAG_INVALID
    ├── CurrentUserDeptService.deptIdOf(owner) → 快照 note.dept_id（无条件写入）
    ├── 若 visibility=INTERNAL 且 dept_id 为空 → 拒绝（owner 无部门）
    ├── NoteMapper.insert(note)
    ├── 每个 tagId → NoteTagMapper.insert
    └── 返回 NoteDetailVO（含 version/ownerTags）
```

### 6.2 更新 Note（含 dept_id 刷新）

```
PUT /api/v1/notes/{id}  { content, logDate, visibility, tagIds, version }
    ├── 乐观锁 version 校验
    ├── NoteAuthService 校验可写（owner / EDITOR / ADMIN）
    ├── 字段级权限：EDITOR 禁止变更 visibility/tagIds（比较是否变化）
    ├── dept_id 刷新：
    │     oldVisibility != INTERNAL && newVisibility == INTERNAL
    │     → dept_id = deptIdOf(owner)；owner 无部门则拒绝（NOTE_DEPT_REQUIRED）
    │     oldVisibility == INTERNAL && newVisibility == INTERNAL
    │     → 保持 dept_id 不变（普通编辑不迁移部门，历史留档）
    │     newVisibility != INTERNAL → dept_id 保持原值（保留快照，不置空）
    ├── NoteMapper.updateById
    ├── 标签重建（diff）
    └── 返回 NoteDetailVO（含新 version）
```

> **dept_id 刷新语义（方案 2）**：`dept_id` **只在 visibility 由非 INTERNAL 转为 INTERNAL 的当下**刷新为 owner 当前部门；**已存在的 INTERNAL note 在普通编辑时保持原部门快照**（不因每次编辑追新部门，避免 owner 调岗导致历史日志漂移）。owner 调岗后：新转 INTERNAL 的 note 归新部门；旧 INTERNAL note 保持原快照，原部门同事仍可见（历史留档，产品决策见 §12 #31）。owner 无部门时：允许编辑已有 INTERNAL note 的 content（不触发刷新、不报 `NOTE_DEPT_REQUIRED`）；仅当本次**新转** INTERNAL 且无部门才拒绝。测试：A 部门建 PRIVATE → 调 B → 改 INTERNAL → dept_id=B；A 部门建 INTERNAL → 调 B → 只改 content → dept_id 保持 A；owner 无部门编辑已有 INTERNAL → 成功；owner 无部门新转 INTERNAL → 50017。

### 6.3 列表查询（visible 与 isVisible 同源 + 7 scope）

```
GET /api/v1/notes?scope=xxx
    ├── mine:         owner_id=me AND deleted=0 AND archived=?
    ├── shared:       owner_id IN (grantor 子查询, grantee=me, APPROVED) AND deleted=0 AND archived=0
    ├── explore:      visibility='PUBLIC' AND deleted=0 AND archived=0
    ├── internal:     visibility='INTERNAL' AND note.dept_id IN visibleDeptIds(me) AND deleted=0 AND archived=0
    ├── subordinate:  owner_id IN subordinates(me) AND deleted=0 AND archived=0
    │                 （开关开才可用；开关关→403）
    ├── visible:      NoteVisibilitySqlBuilder.visiblePredicate(me)   ← 与 isVisible 同源
    │                 AND deleted=0 AND archived=0
    │                 // 下发下属分支含 managerCanReadSubordinate 条件；开关关静默省略，不403
    ├── all:          deleted=0 AND archived=?（仅 ADMIN；archived=true 时查全部归档）
    ├── 统一兜底：非 mine 显式 deleted=0 AND archived=0
    ├── tag 过滤:     mine 按 tag_id/name；其余按 tag.name
    ├── date/keyword/sort 白名单/分页（size≤100）
    └── 批量装配标签 + 权限字段 + version
```

**archived=true**：仅 `mine` 有意义；其余 scope 忽略该参数。

**visible 归档语义**：`scope=visible` 语义精确定义为"**`isVisible && archived=0`**"的 note，故统一 `archived=0`；owner/admin 查看自己归档走 `scope=mine&archived=true`，不混入 visible。

**`visible` SQL/Java 等价闭环**：`NoteVisibilitySqlBuilder.visiblePredicate(me)` 必须与 `NoteAuthService.isVisible(note, me)` 逐分支对应，并满足：

- `visible = isVisible && archived=0` 是**明确语义**，不是"与 isVisible 完全一致"（避免 owner/admin 的归档 note 在语义上打架）；
- `visibleDeptIds(me)` 为空时：`scope=internal` 直接返回空页（`WHERE 1=0`），`scope=visible` **跳过 internal 分支**、**绝不生成 `IN `**；
- `managerCanReadSubordinate=false` 时，`visiblePredicate` 中的 subordinate 分支必须消失（静默省略，不 403）；
- `archived=0` 是 visible 的额外条件，不是 `isVisible` 的原始语义；`isVisible` 中"owner/admin 已归档"仍返回 true；
- **对 ADMIN**：`visible` 与 `all` 的**结果集相同**（均排除 deleted/archived）；差异只在**语义/入口**——`visible` 走通用可见性构建器（对 ADMIN `isVisible` 恒 true），`all` 是 ADMIN 专用全量入口。**不要求两者不同的 SQL 分支**。

**Java/SQL 等值测试矩阵（15 行，列表 vs 详情必须同源；断言 `visible = isVisible && archived=0`）**：

| 场景                                                                | 列表 scope=visible                    | 详情 isVisible             |
| ----------------------------------------------------------------- | ----------------------------------- | ------------------------ |
| owner 自己的 PRIVATE                                                 | 出现                                  | true                     |
| 他人 PRIVATE，无 grant                                                | 不出现                                 | false                    |
| grant VIEWER（他人 note）                                             | 出现                                  | true                     |
| **grant EDITOR（他人 note）**                                         | 出现（EDITOR 仅字段受限，不影响可见）              | true                     |
| INTERNAL 父看子                                                      | 出现                                  | true                     |
| INTERNAL 子看父                                                      | 不出现                                 | false                    |
| PUBLIC                                                            | 出现                                  | true                     |
| subordinate 开关开（他名下属）                                             | 出现                                  | true                     |
| subordinate 开关关（他名下属）                                             | 不出现                                 | false                    |
| ADMIN 全量                                                          | 出现（未归档）                             | true                     |
| **`visibleDeptIds(me)` 为空、note 为 INTERNAL（非 owner/非 grant）**      | 不出现（`scope=visible` 跳过 internal 分支） | false（internal 判定 false） |
| **`visibleDeptIds(me)` 为空、note 为 PUBLIC/owner/grant/subordinate** | 按对应分支照常（不生成 `IN `，只跳过 internal）   | true（按对应分支）              |
| deleted=1（任意角色，**含 owner**）                                       | 不出现（`isVisible` 第 0 步前置）            | false                    |
| 已归档、非 owner                                                       | 不出现                                 | false                    |
| 已归档、owner/admin                                                   | 不出现（visible 定义）                     | true（isVisible）          |

> 该矩阵写入阶段二单元测试，保证实现期不回归；含 grant EDITOR 与 deleted=1 场景。

### 6.4 授权流程（mode + 自动通过记录）

```
场景 1 mode=OWNER：POST /note-grants { granteeId:B, role:VIEWER }
  → grantor=me, status=APPROVED, approved_by=me, approved_at=now
场景 2 mode=SUPERIOR：POST /note-grants { grantorId:A, role:VIEWER }
  → 校验 subordinates(B).contains(A) → grantee=B, status=PENDING
  → A approve → APPROVED
场景 3 mode=ADMIN：POST /note-grants { grantorId:A, granteeId:B }
  → created_by=ADMIN, status=APPROVED, approved_by=ADMIN
  → 审计 + ADMIN 在 tab=created（created_by=me）可见、可代撤销（`direction` 不参与 Tab 过滤）；**grantor A 经 tab=granted 可见含 PENDING 行并确认/拒绝/撤回（grantor==me）**

撤销：DELETE → REVOKED（撤 PENDING 也可）
升级：POST /upgrade → EDITOR（仅 APPROVED）
```

### 6.5 附件与删除完整性（canBind 分离 + 权限矩阵）

```
删除 note → NoteCommandService.delete（事务）级联清理 + SET deleted=1
绑定 task → FileService.bindToTask(fileIds, taskId)   //  增量绑定，不做全量替换
  ├── 新增 fileId：assertCanBind(fileId, me)（创建者/ADMIN）→ 否则 403；不存在→404；已被其他 task 绑定→409
  ├── 已属本 task → no-op（不重复校验 canBind）
  ├── fileIds==null → 不动附件；移除单个附件用 unbindTaskAttachments(taskId, removedIds)
  └── TaskApplicationService 侧已 assertWritable
上传 task 附件 → POST /tasks/{taskId}/attachments → assertWritable → FileService.upload(file, taskId) 返回 FileVO
删除 task → deleteByTask(taskId)：有 note 引用（经 guard）→只解 task_id；无→物理删
移除单个 task 附件 → DELETE /tasks/{taskId}/attachments/{id}（assertWritable + canRemoveFromTask）→ unbindTaskAttachments
删除附件通用入口 → /files/{id} delete：canDelete + 双引用保护（经 AttachmentReferenceGuard，被引用拒绝并提示解除关联）
移除 note 关联 → DELETE /notes/{id}/attachments/{attachmentId}（owner/ADMIN，NoteAttachmentVO.canRemoveRef）
下载/预览 → 仅 /files 用 canReadGeneric（创建者/ADMIN）；note/task 走各自专用端点（各自先校验 isVisible）
孤儿清理 → FieldCleanupJob：显式 SQL 查 task(id,deleted) 判悬挂；扫 attachment 行 24h + 旧磁盘 24h
```

## 7. 权限模型

| 可见性      | 登录用户 | owner | grant VIEWER | grant EDITOR | 同部门(含子树) | 下属（开关开） | 下属（开关关） | ADMIN |
| -------- | ---- | ----- | ------------ | ------------ | -------- | ------- | ------- | ----- |
| PRIVATE  | 否    | 读/写   | 读            | 读/写*         | 否        | 读（方案A）  | 否       | 读/写   |
| INTERNAL | 同部门读 | 读/写   | 读            | 读/写*         | 读        | 读       | 否       | 读/写   |
| PUBLIC   | 读    | 读/写   | 读            | 读/写*         | 读        | 读       | 否       | 读/写   |
| 已归档      | 否    | 读/写   | 否            | 否            | 否        | 否       | 否       | 读/写   |
| 已删除      | 否    | 否     | 否            | 否            | 否        | 否       | 否       | 否     |

\* EDITOR 字段级受限。

同部门列：`note.dept_id ∈ visibleDeptIds(currentUserDept)`（方案 2，本级+子级）。

**关键规则**：

1. **开关即授权**：开关开时上级读下属日志，无需 grant 前置。
2. **PRIVATE = 方案 A**。
3. **INTERNAL = 方案 2**：`note.dept_id ∈ visibleDeptIds(me)`，列表与 isVisible 同源。
4. **deleted 前置**：`isVisible` 开头 `deleted==1 → false`。
5. **归档提前**：owner/admin 后立即判断 archived。
6. **dept_id**：`CurrentUserDeptService.deptIdOf`，禁 `UserContext.deptId`。
7. **ADMIN 有 `scope=all`**（含 `scope=all&archived=true` 列全部/他人归档）+ 可按 ID 访问。
8. **AUDITOR 视为普通 note 用户（方案 A）**：`isVisible`/创建/列表/详情/授权等接口**不因 AUDITOR 角色额外拒绝**——可读自己的、PUBLIC、被授权的日志，可创建/编辑自己的日志；导航显示 note 分组（见 §10.3），不隐藏、不灰态。AUDITOR 的"审计"职责限定在审计模块自身，与 note 访问权限解耦。
9. **`scope=visible` 与 isVisible 同源**，可读不可见一律不进列表。

## 8. 错误码

```java
NOTE_NOT_FOUND(50001, "工作日志不存在"),
NOTE_CONTENT_TOO_LONG(50002, "日志内容超过 20000 字符"),
NOTE_TAG_INVALID(50003, "标签不存在或不属于日志所有者"),
NOTE_ATTACHMENT_INVALID(50004, "附件不存在或不属于当前用户"),
NOTE_ATTACHMENT_NOT_LINKED(50005, "附件未关联到该工作日志"),
NOTE_RELATION_TARGET_NOT_FOUND(50006, "引用目标不存在或不可见"),
NOTE_GRANT_DUPLICATE(50007, "已存在对该用户的授权（待确认或已生效）"),
NOTE_GRANT_SELF(50008, "不能给自己授权"),
NOTE_GRANT_NOT_FOUND(50009, "授权记录不存在"),
NOTE_GRANT_NOT_PENDING(50010, "授权记录非待确认状态"),
NOTE_GRANT_NOT_SUBORDINATE(50011, "只能向下属发起授权"),
NOTE_LOG_DATE_INVALID(50012, "日志日期业务非法（未来超 7 天）；格式不可解析（如 2026-02-30）走 HTTP 400"),
NOTE_FIELD_FORBIDDEN(50013, "当前角色无权修改该字段"),
NOTE_GRANT_FLOW_FORBIDDEN(50014, "授权状态机不允许该操作"),
NOTE_GRANT_MODE_INVALID(50015, "授权发起模式或其必填字段不合法"),
NOTE_SCOPE_DISABLED(50016, "当前作用域不可用（如下属可见开关关闭）"),
NOTE_DEPT_REQUIRED(50017, "INTERNAL 日志需要 owner 有部门"),
NOTE_ATTACHMENT_CANNOT_BIND(50018, "附件不可绑定（HTTP 权限层：附件创建者≠me 且非 ADMIN）；HTTP 走 403，不进业务码"),
NOTE_ATTACHMENT_BOUND(50019, "附件已被其他 task 绑定（task 场景，非 note）"),
TASK_ATTACHMENT_NOT_LINKED(50020, "附件不属于该任务"),
TASK_ATTACHMENT_FORBIDDEN(50021, "通用文件上传不允许携带 taskId；请走任务附件专用端点"),
```

**前端**：`errorCode.ts` 同步映射，文案统一"工作日志"。

## 9. 依赖引入

**无新增依赖**（不做 markdown，不需 flexmark-java）。

## 10. 前端页面与交付清单


### 10.1 前端文件

```
frontend/src/
├── views/note/
│   ├── NoteListView.vue        # 列表壳（布局）：壳内渲染 NoteList（透传 notes/loading/error/page/total/emptyText/activeId 与 open/edit/pin/archive/unarchive/delete/page-change/retry 事件）+ 壳内渲染 NoteFilterBar+ #composer/#above-list/#editor 插槽（#above-list 仅 mine 且非 archived，#editor 为编辑宿主；不声明 open-editor 壳事件，编辑详情经 `useNoteActions.onOpenEditor` 回调）；scope/archived 读 route.meta（7 个 scope 页共用）
│   ├── NoteHomeView.vue        # mine；右栏 360px = BbMiniCalendar(迷你日历，点日过滤 mine 当日) + 当日日志（消费方）
│   ├── NoteExploreView.vue     # explore
│   ├── NoteSharedView.vue      # shared
│   ├── NoteInternalView.vue    # internal
│   ├── NoteSubordinateView.vue # subordinate
│   ├── NoteAllView.vue         # all（仅 ADMIN）
│   ├── NoteUserGrantView.vue   # 授权管理（Tab：我发起的 tab=created / 我授权的 tab=granted / 涉及我的 tab=incoming / ADMIN tab=all）
│   ├── NoteCalendarView.vue    # 全页日历（useMonthCalendar，scope 选择器 + 点日跳转）
│   ├── NoteArchivedView.vue    # 归档区：/notes/archived=mine+archived；ADMIN 再加 /notes/archived/all=scope=all&archived=true（分段即两路由，返回/刷新天然保留）
│   └── NoteDetailView.vue      # 详情
├── components/note/
│   ├── NoteEditor.vue
│   ├── NoteCard.vue
│   ├── NoteList.vue            # 列表容器：loading/empty/error/分页，不发请求（props 驱动）
│   ├── NoteFilterBar.vue       # 共享筛选条（props {query, config, loading?, scope, tagOptions?, tagLoading?, tagError?} + emits change(patch)/retry-tags；7 个 scope 页复用）
│   ├── NoteRelationPicker.vue  # note↔task 引用选择器（见 §5.5 —— note 用 scope=visible，task 用专用 /tasks/search）
│   ├── NoteAttachmentList.vue  # taskId=null + note 关联；走 note 专用下载端点（canUpload/canRemoveRef + maxSize/allowedExt， 同步 §10.5.7）
│   ├── NoteMiniList.vue        #  右栏"当日日志"专属小列表（固定 5 条、无分页、底部"查看全部"跳主列表）；不复用 NoteList（避免 pageSize=5 与固定 20 冲突）
│   ├── NoteCreatedDraftCard.vue #  临时"刚创建"卡片（含附件区/完成/放弃）；从 #above-list 插槽渲染；写入 pendingCreatedNoteId
│   ├── NoteGrantForm.vue       #  授权页三模式表单（OWNER/SUPERIOR/ADMIN），v-model:mode + selected 用户选择 + submit/cancel
│   └── NoteNotFound.vue        #  统一 404 空态组件（详情/附件/引用不可见时共用）
├── components/bb/
│   ├── BbTagPicker.vue           # 从 BbTaskComposer 抽取；props 接 options?: TagOption[]+ modelValue: number[]（已选 tagIds，v-model 双向，多选）+ disabled?，不依赖 store
│   ├── BbUserPicker.vue          # v-model:visible 控制弹窗显隐 + v-model:selected 绑定已选用户（非 modelValue，避免与现有 defineModel<boolean> 冲突）
│   └── BbMiniCalendar.vue        # 改造成通用组件（loader/scope/height/selected），任务页零改动
├── composables/
│   ├── useNoteList.ts          # 查询参数/请求/分页/竞态取消（7 个 scope 页共用）
│   ├── useNoteFacets.ts        #  标签 facet 统一加载 composable（按 scope 调 GET /notes/tags?scope=，loading/error/retry 三态，缓存 60s）
│   ├── useNoteDetail.ts        #  详情加载/竞态取消（AbortController + reload）；返回 {detail,loading,error,notFound,reload}
│   ├── useNoteActions.ts       #  open/edit/pin/archive/remove/刷新/toast 动作单点（7 页共用）；edit 前复检 canEdit、pin/archive/remove 前复检 canManage
│   ├── useNoteGrants.ts        #  授权页数据 composable（GET /note-grants?tab=&page=&size=，分页/筛选/错误类型）
│   ├── useFileConfig.ts        #  附件配置加载（GET /files/config，loading/error/fallback 常量 50MB/空数组）
│   └── useMonthCalendar.ts     # 见 §10.1 契约
├── stores/
│   └── note.ts                 # Pinia：查询状态、缓存、scope 切换清空规则
│                               #  状态契约：
│                               #   state: queryByScope: Record<NoteScope, NoteQuery>  // 含 page/size（页码只存 queryByScope[scope].page，单一事实源；切 scope 保留各自 query，不重置）
│                               #          cache: Map<string, { data: PageResult<NoteBrief>; updatedAt: number }>  // key = scope+stableStringify(query)；useNoteList 读取 cache，命中且 TTL<60s 直接返回、否则请求；LRU 上限 20 key；命中缓存也 clamp page；**cache 命中时只做纯计算 clamp（不隐式 `setPage`），`page > totalPages` 时返回空页并标记 `needsReload`，由调用方显式 `reloadFirst()`，避免缓存命中时改页触发请求**
│                               #          activeNoteId: number | null  // 当前选中 note（跨页高亮/详情返回恢复；`setActiveNote(id)` 写入；`persistSession` 写 sessionStorage）
│                               #          pendingCreatedNoteId: number | null  // 新建后临时卡片；恢复时先 `GET /notes/{id}` 拿 `NoteDetailVO`；`persistSession` 写 sessionStorage
│                               #   actions: setQuery(scope, patch) / setPage(scope, page)  // store 只存状态，页面禁止直接调，所有写入经 useNoteList（URL/请求/竞态单一入口）
│                               #          setActiveNote(id: number | null)  //  
│                               #          invalidate(noteId: number, visibility?: NoteVisibility)  // 按 noteId+可见性失效相关 scope（PUBLIC 失效 mine+explore+shared+visible+all；INTERNAL 失效 mine+internal+visible+all；其余至少失效 mine+对应 scope+all）
│                               #          persistSession / restoreSession  // activeNoteId + pendingCreatedNoteId 写入/恢复 sessionStorage（key 带用户 id）
│                               #          reset  // 退出登录/切换用户时清空全部 + 清 sessionStorage + **清 cache（reset 必须调 `cache.clear()` 清空所有 key）**
│                               #          **cache action**：`cache.delete(key)` 删除单个 key、`cache.clear()` 清空全部、`evictIfNeeded()` 内部 LRU 淘汰（超 20 key 时按 `updatedAt` 最旧优先淘汰 + revoke blob URL）；这三个 action 由 `useNoteList` 的 `invalidate`/`setCache` 内部调用，`reset()` 调 `cache.clear()`；补"切换用户后 cache 为空"单测（用户 A 登录→加载列表→退出→用户 B 登录→cache.keys 为空）
│                               #   useNoteList 是分页/筛选/URL 同步/竞态取消/缓存读取的唯一入口（isolated:true 使用独立内存 cache，非 store.cache）
├── api/
│   ├── note.ts
│   ├── calendar.ts
│   ├── file.ts                 #  附件上传/blob 下载（fetchAttachment/openAttachment/uploadFile/uploadTaskFile）
│   └── task.ts                 #  task 附件端点 + GET /tasks/search 引用搜索
├── types/
│   ├── api.d.ts                # OpenAPI 生成（pnpm gen:types）
│   ├── note.ts                 #  NoteQueryForm/NoteQueryPatch/NoteCalendarScope/NoteDraft/NoteTargetRef/branded type（AttachmentRefId/FileId）
│   └── tag.ts                  #  TagOption 类型（{id?:number;name:string;count?:number}，id 可选覆盖非 mine facet）
├── errorCode.ts                #  50001-50021 错误码映射 + 文案（修改既有文件）
└── router/
    ├── routes.ts               #  note 路由表（修改既有文件，补 meta.title/requiresAuth/:id(\d+) 约束 + RouteMeta 声明 module/scope/archived）
└── index.ts                 #  路由守卫（403 重定向保留 from：`router.replace({ path:'/403', query:{ from: to.fullPath } })`）
```

> **列表架构**：任务侧是 `TaskView + BbTaskList + route.name 映射 scope`；note 侧改为 **`NoteListView`（壳）→ `NoteList`（容器）→ `NoteCard`（卡片）→ `useNoteList`（查询/分页/竞态）→ `stores/note`（跨页状态）**。`NoteHome/Explore/Shared/Internal/Subordinate/All/Archived` 只保留「scope 配置 + 页面标题 + 权限分支」，复用 `NoteListView`，**不重复实现搜索/标签筛选/排序/分页/加载/空态/错误重试/URL 状态**（筛选 UI 统一用共享 `NoteFilterBar`）。点日跳转、from 详情返回列表的筛选与页码都来自 `stores/note`，**不丢失**。

**关键说明**：

- **日历（通用化改造）**：把 `BbMiniCalendar` **改造成通用组件**，任务页零改动、note 版传自己的 `loader`：
  ```ts
  props: { loader?: (year: number, month: number, scope?: string) => Promise<Array<{date:string;count:number}>>;
                                                                    // loader 可选：缺省用组件内任务默认 loader（忽略 scope）；note 传自己的日历取数
           scope?: NoteScope;   // 仅 note 传入；组件内 watch(() => props.scope, () => load()) 重新取数（含默认 loader 时按 scope 变化重建请求）
           height?: number;     // 默认 280；用 :style="{ '--bb-calendar-height': height+'px' }" 注入 CSS 变量，
                                // 替代现有固定 280px scoped CSS（:66-92），任务页默认不变
           selected?: string | null }
  emits: { (e:'select-date', date: string|null): void }   // 点日带出（date 形如 YYYY-MM-DD）
  ```
  **loader 缺省语义**：`loader` 为**可选 props**；**不传时使用组件内置任务日历默认 loader**（任务页零改动、忽略 `scope`）；传入时 task/note 自定义取数。**`scope` 变化总是触发 `load()` 重新取数**（无论用默认 loader 还是自定义 loader）。
    
  **竞态与回归**：`load()` 用请求序号/`AbortController` 取消上一请求，**旧月份响应迟到不得覆盖新月份**；`loader` 抛错 → 显示卡片级错误态 + 重试按钮；`selected` 从外部变化仅设日期、不强制切月（与现有 `:37-42` 一致）。
    
  `useMonthCalendar(loader, opts?: { initialScope?: NoteCalendarScope; initialYear?: number; initialMonth?: number; initialDate?: string | null; syncRoute?: boolean })`（`initialScope` 类型为 `NoteCalendarScope`，与 `MonthCalendarState.scope` 类型一致） 契约：
  ```ts
  interface MonthCalendarState {
    year: number; month: number; loading: boolean; error: string | null
    days: Array<{ date: string; count: number }>
    selected: string | null
    scope: NoteCalendarScope; setScope(s: NoteCalendarScope): void     // scope 类型为 NoteCalendarScope = Exclude<NoteScope,'visible'>（日历不暴露 visible scope，6 个 scope）；setScope 拒绝非法值
    prevMonth: void; nextMonth: void; reload: void; select(date: string | null): void
    forbidden: boolean  // scope=subordinate 关闭或 scope=all 非 ADMIN 时后端返回 HTTP 403，composable 捕获 HttpError.status===403 置 forbidden=true，页面据此显示空态"下属日志功能未开启/无权限"且不显示重试按钮（不落入 error+重试循环）；scope 切换时重置 forbidden=false
  }
  ```
`useMonthCalendar` 增加 URL 同步职责——`prevMonth/nextMonth/select/setScope` 都写 URL（`/notes/calendar?scope=&year=&month=&date=`）；`initialYear/initialMonth/initialDate` 从 `route.query` 读取（`NoteCalendarView` 调用前归一化后传入）；`syncRoute=true`（默认）时 composable 内部 `watch` route.query 变化并重新加载。`initialYear/initialMonth/initialDate` 解决刷新/后退恢复。竞态取消逻辑放进 `useMonthCalendar`（`loader` 接受 `signal: AbortSignal` 参数或内部用请求序号），`BbMiniCalendar` 只消费状态。改造前补任务页快照/交互基线（6 周、周一、补位不可点、高度 280、selected 外部变化不切月），改造后逐项回归；note 侧只新增 loader/scope，不改变默认布局。
    
  **NoteCalendarView scope 选择器（不含 visible，不提供）**：mine/shared/explore/internal 登录用户可选；subordinate 开关关闭→**隐藏**入口（不显示、不 403 干扰）；all 仅 ADMIN 显示（非 ADMIN 隐藏）。**日历不提供 visible scope 聚合入口（`visible` 无独立列表落地页，仅用于引用选择器搜索与列表查询值，见 §5.3）**。
    
  **`BbMiniCalendar`（迷你日历）note 消费方**：**`NoteHomeView` 右栏 360px = `BbMiniCalendar`（mine scope，传 note loader）+ 当日日志列表**，点日过滤 mine 当日；这是 note 侧唯一使用 `BbMiniCalendar` 的地方（全页日历另用 `useMonthCalendar`）。**右栏"当日日志"用独立实例（`useNoteList({ isolated:true })` 或单独 `GET /notes?date=&page=1&size=5` 轻量查询，独立缓存 key），不共享主列表的 `page/query`——点日历只改右栏当日结果，主列表筛选/页码不受影响**。`useNoteList` 契约定义：

```ts
interface UseNoteListOptions { isolated?: boolean; cacheKey?: string; initialQuery?: NoteQueryPatch & { page?: number; size?: number } }  // initialQuery 为 NoteQueryPatch + {page?,size?}；页码与页大小统一由 initialQuery 控制
interface UseNoteListResult {
  notes: NoteBrief[]; total: number; page: number;
  loading: boolean; error: Error | null;
  query: ComputedRef<NoteQuery>;                     // 当前查询参数（只读）
  setPage(page: number): void;                       // 写下一页 → 内部写 URL + 重新请求
  setQuery(patch: NoteQueryPatch): void;             // 更新筛选/排序等（内部重置 page=1）+ 写 URL + 请求（类型收窄）
  resetPage: void;                                 // 只改 state（page=1），不发请求；需重拉第一页用 reloadFirst
  reloadCurrent: void;                             // 按当前条件重拉、不改页码（refresh 语义）
  reloadFirst: void;                               // 重置 page=1 + 重拉第一页（重拉语义）
  refresh: void;                                   // 等同 reloadCurrent（保留别名，兼容旧调用；useNoteActions.refresh 调此，不改页码）
  **needsReload: ComputedRef<boolean>;               // cache 命中且 page > totalPages 时置 true（只计算 clamp，不隐式 setPage），由页面在 onMounted/watch 中调 reloadFirst 并清零；区分"正常空页"和"需要重载"**
}
```

**分页/筛选写入约定**：`NoteList` 的 `page-change(page)` → `setPage(page)`（内部写 URL `?page=` + 重新请求，返回后仍停在该页）；筛选器变更 → `setQuery({...})`（内部**重置 `page=1`** + 写 URL + 请求）；**`resetPage()` 只改 state（page=1）不发请求**；**`reloadCurrent()` 按当前条件重拉、不改页码**；**`reloadFirst()` 重置 page=1 + 重拉第一页**；`refresh()` 是 `reloadCurrent()` 的别名（保留兼容旧调用）；`query` 只读供 UI 展示，**页面不直接改 `stores/note` 的分页/筛选字段**——`useNoteList` 是分页/筛选/URL 同步/竞态取消/缓存读取的唯一入口，避免 7 页分叉。`isolated:true` 时**不读写 `stores/note` 的主列表状态、也不读写 URL**——只维护实例内状态（`query/page` 为局部 ref，`setQuery` 不写 `route.query`，不监听 URL 变化）；单测补"isolated setQuery 后 route.query 不变"。`setQuery` 入参类型为 `NoteQueryPatch = Omit<Partial<NoteQuery>,'scope'|'page'|'size'>`（运行时剔除 scope/page/size，防绕过 route.meta 和分页单一入口）。`useNoteActions()` 的 `onChanged` 默认调 `reloadCurrent()`，删除/归档导致当前页空时 `clamp page`（`page > totalPages` → `setPage(max(1,totalPages))`，请求返回后若 `page > totalPages` 自动 `setPage(max(1,totalPages))`）。右栏"当日日志"用独立 `NoteMiniList`（固定 5 条、无分页、底部"查看全部"跳主列表，见 §10.5.10 组件契约），**不复用 `NoteList`**（避免 pageSize=5 与固定 20 冲突）；`useNoteList({ isolated:true, initialQuery:{ date: today, page:1, size:5 } })`仍用于右栏数据请求，但渲染走 `NoteMiniList` 而非 `NoteList`。`NoteList` loading/error/empty 优先级：首次加载 loading > error > empty > 列表；刷新时保留旧列表 + 顶部错误条。

- **授权页**：`NoteUserGrantView` + 路由 + 导航 + mock；登录用户 3 个 Tab（我发起的 `tab=created` / 我授权的 `tab=granted` / 涉及我的 `tab=incoming`），ADMIN 额外第 4 个"全部授权"（`tab=all`，见 §10.5.5）。
- **附件组件**：`NoteAttachmentList`（**先保存后传**：新建日志先 `POST /notes` 落库拿到 id 再启用附件区，UI 提示"保存后可添加附件"；不采用先上传再靠 24h 清理的孤儿方案，也避免新建态无实体 id 无法关联）。
- **下载方式**：所有附件下载/预览一律用 axios `responseType:'blob'` 携带 Bearer token（`http` 拦截器已注入），经 `URL.createObjectURL` 触发保存/预览；`downloadUrl` **仅作路径引用，不放入 `<a href>`**；统一走 `fetchAttachment(url)`（见 §5.4 前端下载鉴权）。
- **上传签名（以 §10.5.7/§10.4 `file.ts` 为准，本节不重复定义）**：通用 `POST /files` 拒绝 `taskId`（非空→400），故 `uploadFile` 不带 `taskId` 参数——统一签名 **`uploadFile(file, opts?)` / `uploadTaskFile(taskId, file, opts?)`（`opts={onProgress,signal}`）**；`BbAttachmentList` 用 `uploadTaskFile(taskId, file, opts?)`、note 上传用 `uploadFile(file, opts?)`，不得再传 `taskId`（否则撞 400）。
- **`log_date` 前端口径（一律字符串）**：只按 `YYYY-MM-DD` 字符串处理；`el-date-picker` 用 `value-format="YYYY-MM-DD"`；**禁止 `toISOString()`** 与 `new Date('YYYY-MM-DD')`（UTC 解析会跨日）；日期比较用字典序；日历分组、`date=` 查询、`start/end` 范围三处同口径；`utils/date.ts` 只用于任务 `dueAt/remindAt`，不混用到 `log_date`。补 23:59/00:00、月末、跨年用例。
- **`scope=subordinate` 403 空态**：`errorCode.ts` **补齐 5xxxx 全部码**（文案统一"工作日志"）；后端 `scope=subordinate` 开关关闭时返回 **HTTP 403**（不返回 `ApiResult` 业务码），故**前端不按 `code===50016` 分支**（该分支不可达，50016 仅作后端日志/审计用）；`NoteSubordinateView`/`useNoteList` 改为**捕获 `err.status===403 && route.meta.scope==='subordinate'`** 渲染**页面空态**"下属日志功能未开启，请联系管理员"，**不 toast**。`UserVO.managerCanReadSubordinate: boolean`（随登录用户信息返回，**：确认只保留 UserVO 字段方案，删除"或独立 GET /users/me/features"二选一表述**，明确选 UserVO 字段方案，最小改动）；导航项按 `user.managerCanReadSubordinate` 隐藏。`scope=subordinate` 开关关闭时**对所有人返回 403（包括 ADMIN）**——前端导航对 ADMIN 也按 `managerCanReadSubordinate=false` 隐藏（不例外放行）；同步 §5.1、§10.3、§11.2 Row 12a（ADMIN 无例外）。
- **`BbTagPicker` 标签来源与选中值契约**：`BbTagPicker` **只接受 `options?: TagOption[]` props、不依赖 store**，并**暴露选中值契约：`props { options?: TagOption[]（由 `TagVO[]` 映射为 `TagOption[]`）; modelValue: number[]（已选 tagIds）; disabled?: boolean }` + `emits { 'update:modelValue': [value: number[]] }`——父组件用 `v-model` 双向绑定已选标签**；**标签选择是多选（数组模型，可选多个/清空为空数组），非单选**；`NoteEditor` 用 `v-model` 把 `tagIds` 绑到 `BbTagPicker`——编辑态初值来自 `note.tags.map(t=>t.id)`、创建态为 `[]`；`NoteEditor` 传 `note.ownerTags ?? props.ownerTags` 给 `options`（编辑态详情优先，创建态用 `props.ownerTags`=metaStore.tags；**`ownerTags` 为 `undefined` 时可合法省略/传 `undefined` 给 `options?:`**）；**三态区分**——清空全部标签 → `modelValue=[]`（保存后请求体 `tagIds: []`，**不被当作"未修改"**）、无标签用户可保存空数组、**`options/ownerTags === undefined`（未加载）才告警并禁用保存**，避免把"空数组"误判为"未加载"（见 §10.5.2 单一引用，不再写"缺失但 canEdit=true 时禁用"的旧口径），避免静默丢标签（ADMIN 编辑他人日志能看到 owner 的标签并正确回显）。
- **预览/下载浏览器细节**：抽 `openAttachment({ mode:'download'|'preview', url, fileName, contentType })` 统一处理——(1) **签名增加 `contentType` 参数**（用于 MIME 白名单校验）；(2) **noopener**：`window.open('', '_blank', 'noopener')` 返回 `null` 无法赋 `location`，改为直接 `window.open(blobUrl)` 打开 blob URL（同源、无外部引用风险），或先 `const w = window.open('about:blank')` 再 `w.location = blobUrl`（**不传 noopener**）；预览窗口关闭时 `beforeunload` revoke；(3) **同源校验**：内部复用 `fetchAttachment` 的 URL 校验（拒绝 `^[a-z]+://`、`//`、`..`、反斜杠、`javascript:`）；(4) **文件名三级回退**：`filename*`（RFC 5987 中文编码）→ `filename`（普通）→ `fileName`（前端兜底）；(5) **MIME 白名单**：仅 `image/png|jpeg|gif|webp`、`application/pdf`、`text/plain` 可内联预览；`text/html`/`image/svg+xml` 一律下载或转义纯文本（blob URL 继承应用源，HTML/SVG 中的 `<script>` 可读同源 localStorage）；Office 只下载（fallback）；(6) **引用计数管理**：集中到 `attachmentBlobCache`——`acquire(key)` 返回 blob URL 并 +1 计数、`release(key)` -1 计数、计数归零时 revoke；组件 `onUnmounted` 调 `releaseAll()`；下载在 `a.click()` 后 `setTimeout(revoke, 1000)`、预览在窗口 `load()`/`beforeunload` 或 60s 后 revoke；被拦截时**降级为下载并提示**；大文件内存整块 `Blob` 加大小上限提示。
- 纯文本 `white-space: pre-wrap`，禁 `v-html`；`Ctrl+Enter` 判 `isComposing`。

**NoteAttachmentList 权限表**（上传按钮以 `note.canEdit`（含归档 `isWritable`）驱动，**不按角色直判**）：

| 角色             | 未归档上传 | 归档上传 | 移除关联 |
| -------------- | ----- | ---- | ---- |
| owner          | 是     | 是    | 是    |
| EDITOR         | 是     | 否    | 否    |
| ADMIN          | 是     | 是    | 是    |
| VIEWER / 其他可见者 | 否     | 否    | 否    |

> **按钮口径**：上传=`note.canEdit`（归档时 EDITOR `canEdit=false`）；移除关联=**`NoteAttachmentVO.canRemoveRef`**（owner/ADMIN）。**`canDelete`（物理删）与 `canRemoveRef`（移除关联）在 UI 分开**：EDITOR 自己上传的文件 `canDelete=true` 但 `canRemoveRef=false`，此时不出现"移除"按钮（`v-if="item.canRemoveRef"`）。

> **上传下载链路**：上传 `uploadFile(file, opts?)`（`taskId=null`，见 §10.5.7 签名）先得 `FileVO`（此时无 noteId，仅用于临时展示）→ **再 `POST /notes/{id}/attachments` 关联**、**该响应返回完整 `NoteAttachmentVO`（含 note 专用相对路径 `downloadUrl`：`/notes/{noteId}/attachments/{attachmentId}/download`，配合 `http.baseURL='/api/v1'` 使用，不带 `/api/v1` 前缀）**，前端**以该响应的 `NoteAttachmentVO` 替换刚 push 的临时项**为最终展示。
>   
> **删除关联按钮**：由 **`NoteAttachmentVO.canRemoveRef`（或 `NoteVO.canManage`，owner/ADMIN）** 控制，**不使用 `FileVO.canDelete`**（那表示"可物理删除文件"）。EDITOR 上传的 note 附件对 owner：`canDelete=false` 但 `canRemoveRef=true` 可移除关联；对 EDITOR 自身：`canDelete=true` 但 `canRemoveRef=false` 不可移除关联——按钮据此正确显示。
>   
> **失败处理与幂等**：
>
> - **`POST /notes/{id}/attachments` 幂等**：关联已存在（`UNIQUE(note_id, attachment_id)` 冲突）→ **返回现有 `NoteAttachmentVO`，不 409**；前端 POST 成功但未收到响应时重试，不会重复上传/报错。
> - 关联调用失败 → 前端**回滚已上传文件**（尝试删除刚上传附件记录/文件，不残留孤儿）。
> - **回滚删除附件失败**：记录日志并提示"附件已上传但关联失败"（**不静默吞掉**），交由 `FieldCleanupJob` 孤儿清理兜底。
> - **删除关联失败时保留列表项**并提示（不本地移除）。

### 10.2 路由（相对路径 + name/meta）

按 `DefaultLayout` children（相对路径，与现有任务路由一致）：

```js
// 路由 meta 补 title（壳读 route.meta.title 渲染标题，消除 title 双来源）；
//   notes/:id 加 \d+ 约束防吞静态路由；
//   ：父路由统一 requiresAuth（非仅 ADMIN 两条）
{ path: 'notes',            name: 'NoteHome',        component: NoteHomeView,      meta: { module:'note', scope:'mine',      title:'我的日志',    requiresAuth: true } },
{ path: 'notes/explore',    name: 'NoteExplore',     component: NoteExploreView,   meta: { module:'note', scope:'explore',   title:'探索',       requiresAuth: true } },
{ path: 'notes/shared',     name: 'NoteShared',      component: NoteSharedView,    meta: { module:'note', scope:'shared',    title:'被授权日志',  requiresAuth: true } },
{ path: 'notes/internal',   name: 'NoteInternal',    component: NoteInternalView,  meta: { module:'note', scope:'internal',  title:'本部门日志',  requiresAuth: true } },
{ path: 'notes/subordinate',name: 'NoteSubordinate', component: NoteSubordinateView, meta: { module:'note', scope:'subordinate',title:'下属日志',   requiresAuth: true } },
{ path: 'notes/all',        name: 'NoteAll',         component: NoteAllView,       meta: { module:'note', scope:'all',       title:'全部日志',    roles: ['ADMIN'], requiresAuth: true } },   // ADMIN 守卫（值与 constants.ts 的 ADMIN 常量一致）
{ path: 'notes/grants',     name: 'NoteGrants',      component: NoteUserGrantView, meta: { module:'note',                  title:'授权管理',    requiresAuth: true } },
{ path: 'notes/calendar',   name: 'NoteCalendar',    component: NoteCalendarView,  meta: { module:'note',                  title:'日志日历',    requiresAuth: true } },
{ path: 'notes/archived',            name: 'NoteArchived',    component: NoteArchivedView,    meta: { module:'note', scope:'mine',  archived:true, title:'归档日志', requiresAuth: true } },   // mine+archived
{ path: 'notes/archived/all',        name: 'NoteArchivedAll', component: NoteArchivedView,    meta: { module:'note', scope:'all',   archived:true, title:'全部归档', roles: ['ADMIN'], requiresAuth: true } },  // ADMIN 全部归档（scope=all&archived=true）
{ path: 'notes/:id(\\d+)',  name: 'NoteDetail',      component: NoteDetailView,    meta: { module:'note',                  title:'日志详情',    requiresAuth: true } },   // ：\d+ 约束防吞 /notes/tags 等静态路由
```

> 每路由 `meta.title` 已补全（"我的日志/探索/被授权日志/本部门日志/下属日志/全部日志/归档日志/全部归档/日志日历/授权管理/日志详情"）；`NoteListView` 壳读 `route.meta.title` 渲染标题（`NoteListView` 不接 `title` prop，标题单一来源是 `route.meta.title`；若页面需自定义标题可由 `beforeEnter` 改 `meta.title`）。

> 日历点日跳转写 `/notes/{scope}?date=`，但 mine 的实际路由是 `/notes`（非 `/notes/mine`，项目无该路由）。抽取 `noteListRouteOf(scope: NoteScope | 'archived'): string`（`archived` 不属于 `NoteScope` 的 7 个 scope，参数需显式并入以避免 TS 不可比较）——`mine → '/notes'`、`explore → '/notes/explore'`、`shared → '/notes/shared'`、`internal → '/notes/internal'`、`subordinate → '/notes/subordinate'`、`all → '/notes/all'`、`visible → '/notes'`（visible 无独立路由，fallback mine）、`archived → '/notes/archived'`（`archived` 不属于 `NoteScope`，由调用方显式传 `'archived'`）；日历 `select-date` 跳转用 `router.push({ path: noteListRouteOf(scope), query: { date } })`；补单测"7 scope 点日跳转路由正确"。

> 路由守卫跳 `/403` 时保留 `from`——`router.replace({ path: '/403', query: { from: to.fullPath } })`；登录/授权后可 `router.replace(route.query.from)` 回到原 note 页面；补 e2e"非 ADMIN 访问 /notes/all → /403?from=/notes/all → 登录后回跳"。

> **`NoteArchivedView` 单一组件**（无 `NoteArchivedAllView`）；`NoteAllView`与 `NoteArchivedView`（`route.meta.scope==='all'` 且非 ADMIN 时 403 空态）不能只靠路由守卫——角色缓存过期或直接 API 调用时页面会落入通用错误态；**修法**：两页面补页面级 403 空态（`errorMode='empty'` + `emptyText='仅管理员可查看'` + 不显示重试），API 返回 403 时按 `err.status===403` 渲染空态；保留后端兜底（API 仍校验 ADMIN）。

> **路由 meta 契约**：每路由带 `meta`，**`module:'note'`/`scope`/`archived` 三键必须在路由表显式声明**（`meta` 是三处消费方——`route.meta.scope` 列表单一来源（§10.5.3）、`route.meta.module` 左栏分组（§10.3）、`route.meta.archived` 归档分段（§10.2）——的唯一输入，缺一即 `undefined` 导致无法区分）：

| 路由                    | name            | meta                                                             |
| --------------------- | --------------- | ---------------------------------------------------------------- |
| `/notes`              | NoteHome        | `{ module:'note', scope:'mine', title:'我的日志', requiresAuth:true }` |
| `/notes/explore`      | NoteExplore     | `{ module:'note', scope:'explore', title:'探索', requiresAuth:true }` |
| `/notes/shared`       | NoteShared      | `{ module:'note', scope:'shared', title:'被授权日志', requiresAuth:true }` |
| `/notes/internal`     | NoteInternal    | `{ module:'note', scope:'internal', title:'本部门日志', requiresAuth:true }` |
| `/notes/subordinate`  | NoteSubordinate | `{ module:'note', scope:'subordinate', title:'下属日志', requiresAuth:true }` |
| `/notes/all`          | NoteAll         | `{ module:'note', scope:'all', title:'全部日志', roles:['ADMIN'], requiresAuth:true }` |
| `/notes/archived`     | NoteArchived    | `{ module:'note', scope:'mine', archived:true, title:'归档日志', requiresAuth:true }` |
| `/notes/archived/all` | NoteArchivedAll | `{ module:'note', scope:'all', archived:true, title:'全部归档', roles:['ADMIN'], requiresAuth:true }` |
| `/notes/calendar`     | NoteCalendar    | `{ module:'note', title:'日志日历', requiresAuth:true }` |
| `/notes/grants`       | NoteGrants      | `{ module:'note', title:'授权管理', requiresAuth:true }` |
| `/notes/:id(\d+)`     | NoteDetail      | `{ module:'note', title:'日志详情', requiresAuth:true }` |

> `NoteArchivedView` 两个路由同一组件，**仅靠 `meta.scope`+`meta.archived` 区分 `mine+archived` 与 `all+archived`**；`meta.scope` 必须落在 10.5.3 的 7 scope 取值之一。

> **NoteArchivedView 分段**：`/notes/archived`（mine）与 `/notes/archived/all`（ADMIN 全部）为**两个路由、同一组件**，分段即路由——切"全部归档"→ 跳 `/notes/archived/all`，刷新/返回天然保留分段，无"scope 与路由冲突"；非 ADMIN 不显示"全部归档"分段；`NoteListView` 经 `route.meta.scope` 接 `mine+archived` 或 `all+archived`；空态区分"归档区暂无日志"/"暂无全部归档日志"；从详情返回按当前路由保留分段。

> **路由守卫**：`NoteAllView` 挂 `meta.roles:['ADMIN']`（注意大写，与 `constants.ts` 的 `ADMIN:'ADMIN'` 一致；`router` 用 `auth.hasRole(...roles)` 精确比较），由**前端路由守卫按当前用户角色拦截**（非 admin 进入 → 重定向/提示），**仅作 UX 守卫，不作后端授权依据**。**后端授权兜底**：
>
> - **`scope=all`：仅 ADMIN；非 ADMIN → 403**。
> - **`GET /notes/{id}`：走 `NoteAuthService.isVisible(note, me)`**——可见 → 返回；不可见 / 不存在 / 已删除 → **404**（**不限定非 ADMIN 403**，与 §5.1、§7、§6.3 的 isVisible 模型一致，普通用户可打开自己的/被授权/同部门/下属可见的 note 详情）。
> - 其它 note 页仅需登录。
>
> 补测试：owner、grant VIEWER、grant EDITOR、同部门、下属开关开/关、PUBLIC、ADMIN、无关用户分别访问 `GET /notes/{id}`。

### 10.3 导航

`BbFilterRail` 左栏改为"任务 / 工作日志"**两个分组**（可折叠，折叠状态持久化到 localStorage），按 `route.meta.module` 决定显示哪组（note 路由挂 `meta.module:'note'`）：

- **note 路由下隐藏**任务自定义栏与"新增自定义任务栏"行（避免与任务栏互相污染、误跳 `/allTask`）；
- 底部日历图标按当前模块跳转（note 页 → note 日历）；
- 导航项（**含"探索"**）：我的日志 / **探索** / 本部门 / 下属 / 被授权 / 归档 / 授权管理（ADMIN 全量）；`scope=subordinate` 开关关闭时该项隐藏；
- **角色可见性矩阵（AUDITOR = 普通 note 用户，方案 A）**：
  | 导航项                         | 登录用户 / AUDITOR        | ADMIN                                                              |
  | --------------------------- | --------------------- | ------------------------------------------------------------------ |
  | 我的日志 / 探索 / 被授权 / 归档 / 授权管理 | ✅                     | ✅                                                                  |
  | 本部门                         | ✅（无部门→进入后空态"暂无本部门日志"） | ✅                                                                  |
  | 下属                          | 仅开关开启                 | 仅开关开启（ADMIN 也按 `managerCanReadSubordinate` 隐藏，无例外） |
  | 全部                          | —                     | ✅                                                                  |
- **`NoteAllView`（全部）仅在 ADMIN 显示**；AUDITOR 与普通用户一致显示 note 分组（`isVisible`/创建接口不因 AUDITOR 拒绝，见 §7），**不再隐藏、不再灰态**；
- 分组默认展开；折叠状态持久化；左栏整体折叠后 note 分组随导航收进折叠态图标；
- **note 计数**本期不发请求（无 `/notes/count` 接口），不显示数量 badge。

> **`managerCanReadSubordinate` 开关下发与兜底**：导航隐藏"下属"需要该开关，但现有 `UserVO` 不含该字段（`user.ts`/openapi `UserVO` 仅 username/mobile/email/deptId/deptName/roleCode）。在 `UserVO` 新增只读字段 `managerCanReadSubordinate: boolean`（**：删除"或独立 GET /users/me/features 二选一"表述，只保留 UserVO.managerCanReadSubordinate 字段方案**）；**同时加后端兜底**——即使前端拿不到该字段，进入 `subordinate` 页仍由 `scope=subordinate` 开关关闭→403 拦截（见 errorCode），页面按"下属日志功能未开启"空态渲染。**即：字段下发用于隐藏入口，403 兜底保证不泄露**，两者并存。

> **`BbFilterRail` 组件契约**：**无 props，完全 route-driven**——读 `route.meta.module`（`'task'|'note'`）决定渲染任务组/note 组、note 下隐藏任务自定义栏与"新增自定义任务栏"行、底部日历图标按模块跳转（note 页 → note 日历）；**分组级折叠状态持久化到 localStorage key `bb.filter-rail.<module>.collapsed`**（左栏整体折叠 key `bb.filter-rail.collapse`）；任务页因改动需回归的 4 项：任务自定义栏不误隐藏/折叠持久化/模块切换、底部日历跳转目标。任务页 `BbAttachmentList` 改动（task 专用上传/blob 下载/`canRemoveFromTask` 删除按钮）一并纳入任务页回归 e2e（见 §11）。


### 10.4 交付清单

| 文件                                                                                                              | 改动                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |         |          |                                                                                                                                                                                                                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/api/openapi.yaml` | note 接口 + schema（7 scope、version、tags/ownerTags、错误码、404 语义、`scope=all&archived=true` ADMIN 归档、引用幂等、附件下载走 blob 不公开直链）；`FileVO` 增加 `downloadUrl/createdBy/canDelete/canRemoveFromTask`，`TaskVO.files` 引用更新；task 专用附件上传/下载/预览/删除端点；`TaskAuthService.isVisible(Long,Long)` 重载；`GET /tasks/search` 引用搜索端点（响应 `{list,total,page,size}`，status 复用 task schema）；`NoteAttachmentVO/NoteRelationVO/NoteBacklinkVO/NoteDetailVO/NoteBrief/NoteUserGrantVO` 全字段（含 `NoteVO.contentSummary`，见 §10.4/§10.5）；创建/更新/详情统一返回 `NoteDetailVO extends NoteVO`；`GET /note-grants` 增加 `tab=created`、`granted`、`incoming`、`all` 意图参数；新增 `GET /files/config`（下发 maxSize/allowedExt，OpenAPI 注明 `maxSize:number`（字节）与 `allowedExt=[]`=不限制）；`UserVO` 新增 `managerCanReadSubordinate:boolean`（导航隐藏下属入口用，见 §10.3）；新增 50020/50021 错误码 |  |  |  |
| `frontend/src/mocks/handlers.ts` + `fixtures.ts`                                                                | note mock（visible 开关、附件 canBind 403、INTERNAL 方向、404）；**补附件端点 mock**：`GET /files/:id`、`/files/:id/preview`、`POST/GET/DELETE /tasks/:taskId/attachments...`、`POST/GET/DELETE /notes/:id/attachments`、`GET /tasks/search`（含 `{list,total,page,size}` 结构）；用 `HttpResponse.arrayBuffer` 返回二进制并设 `Content-Type`/`Content-Disposition`；**专门造 4 条 blob 用例：200 正常文件、**HTTP 403 + JSON body**、**HTTP 401 + JSON body**（触发 doRefresh）、200 JSON 10002（续签成功重放 / 失败跳登录）**；`fixture` 补 `downloadUrl/createdBy/canDelete/canRemoveFromTask/canManage`，并按八类角色准备数据（owner/EDITOR/VIEWER/ADMIN/同部门/下属开关/PUBLIC/AUDITOR）。**完整端点清单**：A.Note CRUD 5 端点（`POST/PUT/DELETE /notes`、`GET /notes/{id}`、`GET /notes`（列表与 B 组同一端点，不重复计数））；B.列表 1 端点（`GET /notes?scope=&tagId=&tagName=&keyword=&archived=&page=&size=`（`tagId`/`tagName`，与 §5.1/§10.5.3 一致），含 `scope=all` 非 ADMIN 403、`scope=subordinate` 关闭 403、`scope=visible` 关闭静默省略）；C.pin/archive/unarchive 4 端点（`POST/DELETE /notes/:id/pin`、`POST /notes/:id/archive`、`POST /notes/:id/unarchive`）；D.note-grants 6 端点（`POST/GET /note-grants`、`POST /note-grants/{id}/approve\|reject\|upgrade`、`DELETE /note-grants/{id}`，含 4 Tab 数据集 + 三 mode 字段校验 + 自授权 200/50008+ SUPERIOR 携带 granteeId 400+ 重复 active 50007 + 每 grantor 最多 5 PENDING + 状态机迁移 50014 + 权限 403）；E.日历 1 端点（`GET /notes/calendar`，按 scope/year/month 返回 `[{date,count}]`，含 `scope=all` 非 ADMIN 403、`scope=subordinate` 关闭 403、非法 year/month 400）；F.note 附件 4 端点（`POST /notes/:id/attachments`、`GET /notes/:id/attachments`、`DELETE /notes/:id/attachments/:aid`、`GET /notes/:id/attachments/:aid/download`，blob + 403 + 50001 + 归档 EDITOR 50013）；G.files 通用 5 端点（`POST /files`（携带 taskId → 400，不进入业务码）、`GET /files/config`（`{maxSize:number, allowedExt:string[]}`）、`GET /files/:id`（仅创建者/ADMIN，否则 403）、`GET /files/:id/preview`（同前）、`DELETE /files/:id`（双引用保护：**有 note/task 任一引用→拒绝物理删除（409，提示先解除关联），不自动解关联、不删除文件行/磁盘文件**；task 侧解绑走专用端点 `DELETE /tasks/:taskId/attachments/:id`，不经此端点，见 §5.4 规则 6））；H.task 附件 4 端点（`POST /tasks/:taskId/attachments`（assertWritable 失败 403、不可见 404）、`GET /tasks/:taskId/attachments/:aid/download\|preview`（blob + 不可见 404 + task_id 不匹配 404）、`DELETE /tasks/:taskId/attachments/:aid`（有 note 引用→只解 task_id 保留文件、无引用→物理删、不可写 403、非上传者 403、task_id 不匹配 404））；I.relations+backlinks 4 端点（`GET/POST/DELETE /notes/:id/relations`（添加幂等、自引用 400、目标不可见 50006、归档 EDITOR 50013）+ `GET /notes/:id/backlinks`（不可见/不存在/已删除 50001，不返回空数组））；J.`GET /tasks/search`（keyword<2 字符空列表、大小写不敏感、`{list,total,page,size}` 结构）；K.`DELETE /tags/:id`（有 note_tag+task_tag 引用→事务清两表）。每端点 mock 返回结构与错误码与 §11.2 接口验收矩阵 A-L 节逐行对齐。 |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/types/api.d.ts`                                                                                   | **由 OpenAPI 重新生成**（禁止手写 `any` 兜底），同步 `FileVO.downloadUrl/createdBy/canDelete/canRemoveFromTask`、`NoteAttachmentVO/NoteRelationVO/NoteBacklinkVO/NoteDetailVO/NoteBrief`（含 `canManage`/**`contentSummary`**）、`NoteUserGrantVO` 全字段、`GET /tasks/search` 响应 `{list,total,page,size}`、`UserVO.managerCanReadSubordinate`、`/files/config` 响应（`maxSize:number` 字节、`allowedExt` 空=不限制）、创建/更新返回 `NoteDetailVO`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/api/http.ts`                                                                                      | (1) 成功拦截器改 async——`responseType==='blob'` 且响应头 `X-Api-Result: 1`（或 `Content-Disposition` 明确为错误标记）且 `JSON.parse` 成功且结构匹配 `ApiResult` schema（含 code+message+traceId）才按 ApiResult 处理，否则原样返回 Blob（防合法 .json 附件被误判）；code===10002/20005 复用 `doRefresh()` + `_retried` 重放、其余非 0 抛 `BizError`；(2) **新增 `HttpError extends Error` 类**（`{status,data?,message,traceId?}`）——错误拦截器对 HTTP 401/403/400/404 协议级错误按 HTTP status 抛 `HttpError`（非 `BizError`），业务码错误（HTTP 200 + code≠0）仍抛 `BizError`；页面统一读 `err.status===404/403`（见 §5.4）；(3) **401 刷新条件收窄**：仅 HTTP 401 且 code∈{10002,20005} 才 `doRefresh()`，其余 401 直接 `redirectToLogin()`；(4) **普通 JSON 10002 统一处理**：成功拦截器无论 `responseType` 是 json 还是 blob，只要 code===10002/20005 都复用 `doRefresh()` + `_retried` 重放；(5) 错误拦截器也处理 Blob——若 `error.response.config.responseType==='blob'` 且 `data instanceof Blob`，先 `await data.text` 解析再按 HTTP status 分流                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/e2e/` | 全场景 + 乐观锁 version 闭环 + **blob 下载（200 文件 / HTTP 403 + JSON body / HTTP 401 + JSON body / 200 JSON 10002 续签 / 中文文件名，见 §5.4）**、引用选择器（防抖/已引用禁选/task 搜索/排除自引用/backlinks 来源跳转）、左栏模块分组（含探索；AUDITOR 按普通用户显示）、卡片按钮矩阵（owner/EDITOR/VIEWER/ADMIN/归档 EDITOR/归档 owner 六种）、角色×字段按钮矩阵、授权按钮矩阵与二次确认（含 ADMIN 代授权在我发起的可见、无标签可保存、ADMIN 回显 owner 标签）、行内编辑全文不截断（20 行）、上传进度/取消/3 并发、日历 scope 切换点日保留 scope/竞态、任务页 BbMiniCalendar 回归、归档分段刷新/返回保留、`VERSION_CONFLICT` 查看最新保留草稿、`log_date` 跨月/月末、新建先保存再传、ADMIN 全归档、subordinate 403 空态、**标签选中值闭环：编辑含 2 标签的 note→打开回显 2 个→移除 1→保存请求体 `tagIds` 剩 1；新建选 3 标签→列表/详情回显 3；清空全部标签→请求体 `tagIds: []` 不被当"未修改"** |  |  |  |
| `frontend/src/api/file.ts`                                                                                      | **`fetchAttachment(url): Promise<{blob:Blob; fileName:string; contentType:string}>`+ `stripApiPrefix()`**；**上传签名**：`uploadFile(file, opts?)` / `uploadTaskFile(taskId, file, opts?)`（`opts={onProgress,signal}`，支持进度/取消）；新增 `fetchFileConfig()`（`/files/config`）；**branded type：`AttachmentRefId`/`FileId` 品牌类型定义在 `types/note.ts`，`NoteAttachmentVO.id: AttachmentRefId`、`NoteAttachmentVO.attachmentId: FileId`、`remove(attachmentId: FileId)`（branded type 落到 `attachmentId` 非 `fileId`），类型系统阻止误传 `id`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/utils/errorCode.ts`                                                                               | 补齐 5xxxx 全部映射（含 `NOTE_NOT_FOUND(50001)` 等常量，按 code 分支不依赖 HTTP status）+ 枚举中文文案（visibility/grantRole/status/mode，文案统一"工作日志"，见 10.5.9）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/router/routes.ts`                                                                                 | note 页（children 相对路径 + **`meta.module:'note'` + `meta.scope` 单一来源 + `meta.archived`，见 10.2 路由 meta 表**）；`notes/calendar` 支持 `scope/year/month/date` query；**新增 `/notes/archived/all`（ADMIN 归档）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/stores/note.ts` + `composables/useNoteList.ts`                                                    | 列表查询状态/分页/URL 同步/**竞态取消**/排序与置顶筛选（7 页共用，10.5.3）；**scope 以 route.meta.scope 为唯一来源、URL 不写 scope、缓存 key=scope+query**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/views/note/NoteListView.vue` + `components/note/NoteList.vue`                                     | 列表壳 + 容器；**`NoteListView`（壳，壳内渲染 `NoteList` + 壳内渲染 `NoteFilterBar` + 透传数据/事件，提供 `#composer`/`#above-list`/`#editor` 插槽，读 `route.meta`，页面 owns 编辑器与保存， 方案 A + /），`NoteList` 纯容器（loading/empty/error/页码分页，无 composer/筛选器/scope、无 `size-change`/`pageSize`，），10.5.1/10.5.10**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/bb/BbFilterRail.vue`                                                                   | 改为"任务 / 工作日志"两分组（按 `route.meta.module`，含"探索"，折叠持久化；**AUDITOR 与普通用户一致显示 note 分组，不隐藏（方案 A）**），note 下隐藏任务自定义栏；底部日历按模块跳转                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/bb/BbUserPicker.vue`                                                                   | 新增 `multiple?: boolean` 与 `scope?: 'subordinate'`（授权单选/下属过滤）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/bb/BbTagPicker.vue`                                                                    | 抽取；不依赖 store；**完整契约：`props { options?: TagOption[]; modelValue: number[]; disabled?: boolean }` + `emits { 'update:modelValue': [value: number[]] }`**（`options` 由 `TagVO[]` 映射，`undefined`=未加载、`[]`=合法空；`modelValue` 为已选 tagIds 多选；父组件用 `v-model` 绑定）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/composables/useMonthCalendar.ts`                                                                  | `MonthCalendarState` 契约（scope/setScope/loader 工厂，见 §10.1）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/bb/BbMiniCalendar.vue`                                                                 | **通用化**：`loader?`（**可选：缺省用内置任务默认 loader、忽略 scope，传入则自定义**）/`scope`/`height`/`selected` props（height 走 `--bb-calendar-height` CSS 变量）+ `watch(scope, load)`（scope 变化重取）+ 竞态取消                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/note/NoteAttachmentList.vue`                                                           | note 附件（走专用端点；**上传后以 `POST /notes/{id}/attachments` 返回的 `NoteAttachmentVO` 为准展示**；上传=`note.canEdit`、移除=`canRemoveRef`；多文件/大小类型限制/进度/重试/未保存禁用态，见 10.5.7）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `frontend/src/components/note/NoteRelationPicker.vue`                                                           | 引用选择器：note 用 `scope=visible`，task 用 `GET /tasks/search`；防抖/已引用置灰/排除自引用/**内部 NOTE·TASK Tab（`defaultMode` 仅初始、mode 全内部，按 targetType 过滤已引用）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../config/web/SpaRoutes.java`                                                                         | PAGE_PATTERNS + SAMPLE_PAGES 补 `/notes`、`/notes/**`（含详情 `/notes/:id` 与 `/notes/archived/all`，刷新不返回 401 JSON）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../config/LogicDeleteGuard.java`                                                                      | 4 张 note 关系表入"禁止 deleted"清单                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../modules/file/application/FileService.java` | 与实体无关的 **canBind/canReadGeneric/canDelete**（均创建者/ADMIN）；download/preview 最小权限；**`upload(file, taskId)` 返回 `FileVO`（taskId 允许 null；只落盘写 task_id，不做任务权限校验——由调用方 Controller 负责，避免 file→task 依赖倒挂）**；**bindToTask 增量绑定**；`unbindTaskAttachments` 方法契约（`@Transactional` + task 错误码 + 经 `AttachmentReferenceGuard` 判 note 引用）；`delete` 经 `List<AttachmentReferenceGuard>` 双引用保护+分工；deleteByTask 整体删除；孤儿清理 |  |  |  |
| `backend/.../modules/file/application/AttachmentAccessService.java`                                             | 新增：仅与实体无关的 canBind/canReadGeneric/canDelete（**不含 note/task 可见性与引用保护**）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |         |          |                                                                                                                                                                                                                                                                                                                                          |
| 公共 `AttachmentReferenceGuard` 接口                                                                                | `referencedByNote(id)` / `referencedByTask(id)`（common 包，仿 `TagUsageCleaner`）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../modules/note/.../NoteReferenceGuard.java` 与 `backend/.../modules/task/.../TaskReferenceGuard.java` | 分别实现 `AttachmentReferenceGuard`，注入各自 Mapper 判断引用存在                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../modules/file/controller/FileController.java`                                                       | **`POST /files` 拒绝 `taskId`（非空 → 400）并返回 `ApiResult<FileVO>`**；**`GET /files/{id}` 收紧仅创建者/ADMIN**；泛 `DELETE /files/{id}` 保留（双引用保护）；**新增 `GET /files/config` 返回 `{maxSize:number(字节), allowedExt:string[]（空=不限制）}`**；**不放 task 附件端点（类级 /api/v1/files 会路径拼接错误）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |         |          |                                                                                                                                                                                                                                                                                                                                          |
| `backend/.../modules/task/controller/TaskAttachmentController.java`                                             | 类级 `@RequestMapping("/api/v1/tasks")`，方法：                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |         |          |                                                                                                                                                                                                                                                                                                                                          |

- `POST /{taskId}/attachments`：`assertWritable(taskId,me)` → `FileService.upload(file, taskId)` 返回 `FileVO`
- `GET /{taskId}/attachments/{attachmentId}/download\|preview`：task_id 匹配 + `isVisible(taskId,me)`+ 404
- `DELETE /{taskId}/attachments/{attachmentId}`：`assertWritable + canRemoveFromTask` → `unbindTaskAttachments`；`@OperateLog` 下载与删除审计、错误码 50020
    
  或在现有 `TaskController` 加方法 |
    
  | `backend/.../modules/task/application/TaskQueryService.java` + `TaskVO.files` | 返回附件**携带 `downloadUrl`（与 TaskAttachmentController 完全一致的 task 专用路径）**，供前端直连下载；**新增 `GET /tasks/search` 轻量搜索（keyword+分页，内部按 `isVisible(taskId, me)` 过滤）供 note→TASK 引用选择器使用（task 无 `visible` scope，`scope=all` 语义 ≠ isVisible，见 §5.5）** |
    
  | `backend/.../modules/job/application/FieldCleanupJob.java` | 扫 attachment 行 24h + **悬挂 task 引用清理（显式 SQL 查 task.deleted，不依赖 @TableLogic 默认过滤）** + 旧磁盘清理 24h |
    
  | `frontend/src/components/bb/BbAttachmentList.vue` | 上传改走 **task 专用上传端点**（`uploadTaskFile(taskId, file, opts?)`， 与 §10.5.7 签名一致）；下载/预览改走 **axios `responseType:'blob'`**（`downloadUrl` 仅作路径，不 `<a href>` 直连）；删除按钮 **`v-if="file.canRemoveFromTask && !disabled"`**（后端计算，不再单用 `file.canDelete`），删除调用 **task 专用删除端点** `DELETE /tasks/{taskId}/attachments/{id}` |
    
  | `backend/.../modules/task/application/TaskApplicationService.java` | **update 不把 `fileIds` 当全量替换**（`fileIds==null` 或 `[]` 均不动附件）；仍 `assertWritable`；附件增删走独立入口 |
    
  | `backend/.../modules/task/application/TagService.java` | delete 遍历所有 `TagUsageCleaner` |
    
  | `backend/.../modules/task/.../TaskTagCleaner.java` | 清 task_tag（task 模块实现 TagUsageCleaner） |
    
  | `backend/.../modules/note/.../NoteTagCleaner.java` | 清 note_tag（note 模块实现 TagUsageCleaner） |
    
  | 公共 | `CurrentUserDeptService` + `DepartmentPolicy.visibleDeptIds` + `TagUsageCleaner` 接口 |
    
  | note 模块 | `NoteVisibilitySqlBuilder` |
    
  | `Task/02、03、05、06` | 同步更新（ADR-004） |
    
  | 新增 ADR | Note 模块边界 + 访问控制（INTERNAL 方案2 + visible 同源）+ 附件**实体无关判定**（canBind/canReadGeneric/canDelete）+ **AttachmentReferenceGuard 引用保护** + **下载鉴权 blob** + 授权 mode + Service 保证枚举 |

**SpaRoutes 澄清**：note 不需要 SecurityConfig API 白名单，但必须加 `PAGE_PATTERNS`：**`/notes`、`/notes/**`**（浏览器刷新 /notes、/notes/123 无 Authorization 需放行页面壳；`/notes/**` 覆盖 explore/calendar/grants/archived 等子页）。增加 e2e——直接访问 `/notes`、`/notes/123`、`/notes/archived/all`、`/notes/explore`、`/notes/calendar`，断言返回 `index.html`（SPA 壳）且不是 401 JSON（防 SecurityConfig 误拦截 SPA 路由为 401）；`SpaRoutes` 单测覆盖 `PAGE_PATTERNS` 匹配 `/notes`、`/notes/`、`/notes/123`、`/notes/archived/all` 等路径。

**DTO 字段**：

- `NoteVO`：`id/content/**contentSummary**/logDate/visibility/pinned/archived/ownerId/ownerName/ownerDept/**version**/canEdit/canManage/grantRole/createdAt/updatedAt/tags`（tags=本 note 已选；**NoteVO 自身不含 ownerTags**）。**`contentSummary` = 后端固定字符摘要（如 ≤200 字，含换行信息），列表/映射摘要一律取它，前端不自行 `slice` 截断**。
- `ownerTags`：owner 完整标签 `[{id,name}]`——**只出现在 `NoteDetailVO`**（`NoteVO`/`NoteBrief` **均不含**），且**仅 `canEdit==true || canManage==true` 时返回**；列表不返回 ownerTags（口径唯一，避免三处不一致）。
- **返回类型约定**：`GET /notes` → **`PageResult<NoteBrief>`**（列表只给摘要，不给全文，控制首屏体积）；`GET /notes/{id}`、**创建 `POST /notes`、更新 `PUT /notes/{id}` → `NoteDetailVO`**（创建/更新后立即拥有全文/ownerTags/attachments/relations，便于内联展开附件区、重置标签选择器）。**保存成功后返回的 `NoteDetailVO` 仅用于临时"刚创建"卡片与详情展示，不插入/更新列表（列表统一走"保存后重拉第一页"）**。OpenAPI 对创建/更新/详情统一一个 `200` schema，**`NoteDetailVO extends NoteVO`**（继承关系写清，避免三处口径冲突）。`NoteBrief` 与 `NoteCard` 字段一一对应。
- `NoteBrief`：`id/contentSummary/logDate/visibility/pinned/archived/ownerId/ownerName/tags/canEdit/**canManage**/version`（**字段名 `contentSummary`，与 `NoteVO.contentSummary` 同名同义；`NoteBrief` 不含 `content`（全文）字段**，杜绝"详情全文/列表摘要同名字段误用"；**摘要算法：后端取 `content` 前 200 码位（保留换行、不足不补省略号）生成 `contentSummary`，前端不二次截断**）。**列表与详情同口径**：**编辑内容**=`canEdit`（`canEdit` 已含 `!deleted` 与"归档 EDITOR=false、归档 owner/admin=true"规则，**不再叠加 `&& !archived`**）；**置顶/归档/删除（pin/archive/delete）**=`canManage`（owner/ADMIN，EDITOR=false、管理员可管他人日志）；**标签编辑**=`canManage`（EDITOR 禁止）。前端 `NoteCard` 按钮据此渲染，**禁止 `canEdit` 控管理按钮、禁止 `ownerId===me` 推导**（否则 ADMIN 无法管理他人、EDITOR 误显删除会 403）。
- **卡片按钮矩阵（e2e 六身份）**：owner 全显（编辑/置顶/归档/删除，**含归档态仍可编辑**）；EDITOR 仅编辑（`canEdit=true`，管理按钮不显示；**归档态 EDITOR `canEdit=false` 编辑也隐藏**）；VIEWER 只读；ADMIN 全显（即使非 owner，含归档态）；归档 owner/ADMIN 均保留编辑入口（`canEdit` 已含此规则）。
- `grantRole`：VIEWER/EDITOR/OWNER/ADMIN/null=无授权。
- **`NoteUserGrantVO`**（`GET /note-grants` 的元素，由 `tab` 参数区分 created/granted/incoming/all）——授权页按此渲染列与按钮：
  ```text
  id, grantorId, grantorName, granteeId, granteeName
  role        // VIEWER | EDITOR
  status      // PENDING | APPROVED | REJECTED | REVOKED
  mode        // OWNER | SUPERIOR | ADMIN
  direction   // outgoing(grantor_id==me) | incoming(grantee_id==me) | **none**（两者皆非，如 ADMIN 代授权行对 ADMIN；纯粹行内展示，不参与 Tab 过滤）
  createdBy, createdByName, createdAt
  approvedAt, approvedBy, revokedAt, revokedBy
  canApprove  // 待我处理（grantor==me 确认他人发起的 PENDING，出现于 granted 的 PENDING 行）
  canReject   // grantor==me 可拒 PENDING
  canUpgrade  // VIEWER→EDITOR（仅 APPROVED 且 grantor==me）
  canRevoke   // 撤 PENDING：grantor==me OR created_by==me 或 ADMIN；撤 APPROVED：grantor==me 或 ADMIN（grantor 对 granted 的 PENDING 亦可撤回；ADMIN 仅对 PENDING/APPROVED 有"撤销"语义，REJECTED/REVOKED 无）
  ```
  **action 口径**：`can*` 均由后端按「当前用户 + 该行」计算下发，前端只读不推导；**具体按钮按 §10.5.5 按钮矩阵（含 Tab 维度限制）渲染，`can*` 与矩阵并存、矩阵更细**。**SUPERIOR 行 grantor（下属 A）确认/拒绝**，`approved_by=grantor`，A 经 `tab=granted` 始终可见并可撤销/升级，**granted 的 PENDING 行可"确认/拒绝/撤回"**；**发起人 S 可撤回自己发起的 PENDING**（`created_by==me`）。ADMIN 全量视图（`tab=all`）只读列表 + 可代撤销（对 PENDING/APPROVED 任意行；REJECTED/REVOKED 只读）；ADMIN 在 `tab=created` 只对本人发起行可代撤销——`grantor==me` 可升级/撤销，`isAdmin && created_by==me`（ADMIN 代授权行）可撤销不可升级。`GET /note-grants` 仅一个端点，`tab` 参数覆盖 created/granted/incoming/all，**不再有独立 `/pending`、`/all` 端点**。
- **`NoteAttachmentVO`**（`POST /notes/{id}/attachments`、`GET /notes/{id}/attachments` 的元素）：
  ```text
  id            // 关联 id（note_attachment_ref.id）
  attachmentId  // 附件 id
  fileName, size, contentType
  downloadUrl   // 相对 baseURL 路径，如 /notes/{noteId}/attachments/{attachmentId}/download（不带 /api/v1）
  createdBy, createdByName, createdAt
  canRemoveRef  // owner/ADMIN 可移除关联
  canDelete     // 可物理删（上传者/ADMIN）
  ```
  **`NoteAttachmentVO.createdAt/createdBy` = 附件上传时间/上传者**（取自 `attachment` 表，**非关联时间**；UI 文案统一"上传者/上传时间"，见 §10.5.7）。
    
  前端以 `POST /notes/{id}/attachments` 返回的该 VO 替换临时 `FileVO` 起最终展示（`NoteAttachmentVO.id` 是关联 id，**删除传 `attachmentId`**）。
- **`NoteRelationVO`**（`GET /notes/{id}/relations`——"我引用了谁"的元素，填充 **target** 字段）：
  ```text
  id            // note_relation.id，删除用
  targetType    // NOTE | TASK
  targetId
  targetSummary // NOTE 取 contentSummary / TASK 取 title
  createdAt, createdBy, **createdByName**   // 引用时间/引用人（UI 显示"引用人：张三"）
  // 不可见 TASK 目标直接不返回，不要 targetVisible=false 占位
  ```
- **`NoteBacklinkVO`**（`GET /notes/{id}/backlinks`——"谁引用了我"的元素，填充 **source** 字段；`note_relation` 行语义是 `source → target`，backlinks 查 `target=当前note` 的行，故需来源侧字段，与 relations 的 target 语义不同，**不可复用 `NoteRelationVO`**）：
  ```text
  relationId           // note_relation.id
  sourceNoteId         // 来源 note id（点击跳 /notes/{sourceNoteId}）
  sourceSummary // 来源 note 摘要
  sourceOwnerId, sourceOwnerName
  createdAt, **createdBy, createdByName**   // 引用时间/引用人（EDITOR/ADMIN 也可添加引用，"引用人"≠来源 owner；可空显示"—"，与 NoteRelationVO 口径一致）
  // 来源 note 不可见/已删除 → 整条不返回（不占位，不泄露）
  ```
- **`NoteDetailVO`**：`...NoteVO + relations: NoteRelationVO[] + backlinks: NoteBacklinkVO[] + attachments: NoteAttachmentVO[] + ownerTags?: TagVO[]`（有权限时）。
- **`FileVO` / `TaskVO.files`**：附加 `downloadUrl`（**上下文相关、可空、相对 baseURL**——通用 `/files` 上传返回 `/files/{id}`；task 上传返回 `/tasks/{taskId}/attachments/{id}/download`；**仅作路径引用，不做 `<a href>` 直链**）、`createdBy`、`canDelete`（**可否物理删**，创建者/ADMIN，**不含引用保护**）、`canRemoveFromTask`（**task 附件删除按钮口径**，=`assertWritable && (createdBy==me||isAdmin)`；由后端按当前用户+当前 task 计算）。task 附件删除走 `DELETE /tasks/{taskId}/attachments/{id}`。

**前后端契约固化**：上述 VO 字段表写入 OpenAPI，`types/api.d.ts` 由 OpenAPI **重新生成**、**禁止手写 `any` 兜底**；`NoteBrief`/`NoteAttachmentVO`/`NoteRelationVO`/`NoteBacklinkVO`/`NoteDetailVO`/`NoteUserGrantVO` 的 TypeScript 类型为契约来源。


### 10.5 前端页面需求、组件契约与交互明细

#### 10.5.1 页面需求表（页面 × 路由 × scope × 操作 × 可见角色 × 空态）

| 页面   | 路由                                                       | scope                        | 主要操作                                                                                                                                                                                                                     | 可见角色            | 空态文案（默认）             |
| ---- | -------------------------------------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------- | -------------------- |
| 我的日志 | `/notes`                                                 | mine                         | 新建、编辑、置顶、归档、删除、附件、引用                                                                                                                                                                                                     | 登录用户            | 暂无日志，点击上方开始记录        |
| 探索   | `/notes/explore`                                         | explore                      | 查看 PUBLIC；**引用/编辑/管理按钮按 `canEdit/canManage` 渲染（例外：owner 浏览自己的 PUBLIC 时 `canEdit=true`，可编辑、可添加引用；`canEdit=true` 的非 owner（含 EDITOR grant，见 §5.5）也可添加引用；仅"无 `canEdit` 的非 owner"（VIEWER）查看引用、不可添加）** | 登录用户            | 暂无公开日志               |
| 被授权  | `/notes/shared`                                          | shared                       | 查看、按 grant 编辑                                                                                                                                                                                                            | 登录用户            | 暂无被授权给我查看的日志         |
| 本部门  | `/notes/internal`                                        | internal                     | 查看、按权限编辑                                                                                                                                                                                                                 | 登录用户（无部门空态）     | 暂无本部门日志              |
| 下属   | `/notes/subordinate`                                     | subordinate                  | 查看（`grantRole=EDITOR` 的下属 note `canEdit=true` 时可编辑）                                                                                                                                                   | 开关开启            | 下属日志功能未开启            |
| 全部   | `/notes/all`                                             | all                          | 查看、管理                                                                                                                                                                                                                    | ADMIN           | 暂无日志                 |
| 授权管理 | `/notes/grants`                                          | -                            | 发起、确认、拒绝、升级、撤销                                                                                                                                                                                                           | 登录用户（ADMIN 全量）  | 暂无授权记录               |
| 日历   | `/notes/calendar`                                        | 可选                           | 查看、点日跳转                                                                                                                                                                                                                  | 登录用户            | -（日历无空态，有 0 计数）      |
| 归档   | `/notes/archived`（mine）/ `/notes/archived/all`（ADMIN 全部） | mine+archived / all+archived | 取消归档、删除；**归档 owner/admin 亦可编辑 content（`canEdit=true`）**                                                                                                                                                   | 登录用户 / ADMIN 全量 | 归档区暂无日志 / 暂无全部归档日志   |
| 详情   | `/notes/:id`                                             | -                            | 查看、编辑、附件、引用、backlinks                                                                                                                                                                                                    | 可见用户            | -（404 用 NotFound 空态） |

每列默认排序 `log_date DESC, id DESC`；筛选器（见 10.5.3）随 scope 呈现；卡片按钮由 `canEdit/canManage` 驱动（10.5.4）；归档页/全部页跳转目标 = 对应详情。**页面操作表为权限驱动**："主要操作"仅是默认能力描述，**具体按钮一律按 `canEdit/canManage` 渲染（同 10.5.4 矩阵）**，不当固定文案限制；三条例外已并行列内（探索 owner、下属 EDITOR、归档 owner/admin 可编辑）。

**新建/筛选器归属**：**方案 A（页面 owns 交互）**——`NoteListView` 是**布局壳**、**内部渲染 `NoteList`（壳内渲染）+ 壳内渲染 `NoteFilterBar`**，提供 **`#composer` / `#above-list` / `#editor` 具名插槽**、**不内部实例化编辑器、不持有保存/附件状态**；**壳把 `notes/loading/error/page/total/emptyText`（`activeId`，页面可把当前选中卡片 id 传进来，壳原样透传给内部 `NoteList`）透传给 `NoteList` 并显式透传 `open/edit/pin/archive/unarchive/delete/page-change/retry` + `change` + `retry-tags`（共 10 个事件，与 §10.5.10 契约表一致；`change` 透传 NoteFilterBar 筛选变更，`retry-tags` 透传标签 facet 重试）**，页面只传数据 + 监听事件（7 个 scope 页统一走此装配，杜绝每页自写一套 NoteList/事件绑定）；`NoteHomeView` 自己持有 `NoteEditor`（经 `#composer` 插槽放入新建、经 `#editor` 插槽放入行内编辑宿主）、`POST /notes`、保存后清空筛选+重拉、临时卡片附件区状态。壳内 `v-if="route.meta.scope==='mine' && !route.meta.archived"` 才渲染 `#composer`/`#above-list` 插槽；**`NoteListView`/`NoteList` 不接收 `scope`/`archived` props，统一读 `route.meta`**。**新建入口仅 `mine`（NoteHomeView）提供**（`all` 不提供新建：它是管理视图，新建后归属/可见性易混淆；`explore/shared/internal/subordinate/archived` 不提供新建）。**筛选器（`filterConfig`）全员提供**（搜索/标签/排序/置顶/date，`NoteFilterConfig` 见 10.5.10）。**`NoteList` 的 `open/edit/pin/archive/unarchive/delete` 事件由页面在外层处理（壳透传，不在壳内吞掉）；此项动作逻辑抽入公共 `useNoteActions()`——open 跳详情、edit 先 `GET /notes/{id}` 拉详情后**回调 `onOpenEditor(noteDetail)`**（方案 A：页面经 `#editor` 插槽承载 `NoteEditor` 宿主）、pin/archive/unarchive/remove 调 API、刷新与 toast 均由它承载，7 个 scope 页共用同一 composable、只注入 scope 差异（见 10.5.10），杜绝 7 份复制**。

#### 10.5.2 新建/编辑流程与 `NoteEditor` 契约

- **新建入口**：`NoteHomeView` 顶部内联编辑器（仿 `BbTaskComposer`，非独立页非弹窗），经 **`NoteListView` 的 `#composer` 插槽**渲染；`NoteHomeView` **持有 `NoteEditor` 与保存/附件状态**，负责 `POST /notes`、保存后清空筛选+重拉、临时卡片管理等全部交互——`NoteListView` 不接编辑器的保存事件。
- **新建默认值**：**创建默认值只在 `NoteEditor` 内部定义一次**（`logDate=今天（Asia/Shanghai）`、`visibility=PRIVATE`、`tagIds=[]`）；`NoteHomeView` 不重复传（仅在其行为需覆盖时传可选覆盖）。前端先行，不依赖后端默认（`INTERNAL` 无部门时前端预提示，后端 `50017` 兜底；**不得默认 PUBLIC**）。
- **保存行为与"新建后状态机"（方案 A：临时卡片 + 落点固化）**：`NoteHomeView` 保存成功后：**(a)** `POST /notes` 返回 `NoteDetailVO`（含 id）；**(b)** 在列表顶部保留一张**临时"刚创建"卡片**（含 `noteId` + 附件区 `NoteAttachmentList` + "完成" + **"放弃"**按钮），**独立于列表数据、不随重拉消失**；**(c)** 同时**清空关键词/标签/置顶/日期筛选**、**按当前排序重拉第一页**并 toast"已保存"（列表数据照常刷新；若新日志恰在第一页，临时卡片与列表卡片并存——列表卡片无附件入口，附件以临时卡片为准）；**(d)** 用户在临时卡片内完成附件上传/关联，点"完成"或"放弃"或开始输入下一条时移除临时卡片。**不"跳 logDate 所在页"、不在列表本地插入、不做无条件 unshift。** 保证：新日志即使不在第一页，用户仍有明确入口补附件；输入框清空、焦点保留（开始下一条时先完成/放弃第一条，防止附件状态串条）。
    
  **临时卡片落点与跨路由恢复**：临时卡片经 **`NoteListView` 新具名插槽 `#above-list`** 渲染（仅 `route.meta.scope==='mine'` 且非 archived 时渲染，与 `#composer` 同条件；= 编辑器与临时卡片各占一槽，避免同槽职责混杂、组件契约闭合）；`NoteHomeView` 把**临时卡片 noteId 写入 `stores/note.pendingCreatedNoteId`**（持久化到 sessionStorage），**进入详情再返回 `/notes` 或同 scope 刷新时恢复临时卡片**（`onMounted` 读 `pendingCreatedNoteId` 重建；无法恢复时至少给"刚才创建的日志 X 仍可补附件"入口）；新日志已不在第一页时，经恢复的临时卡片仍是明确附件入口。"放弃"按钮 = 移除临时卡片（不回滚已创建的 note，仅放弃当前补附件/展示）。
- **行内编辑先取详情**：列表卡片内容点击（**`canEdit`** 即可，`canEdit` 已含归档 EDITOR=false/owner-admin=true，**不叠加 `!archived`**；管理动作 `canManage`）→ 先调 `GET /notes/{id}` 拿 **`NoteDetailVO`（全文 + ownerTags + version）** 再打开 `NoteEditor`，加载期间卡片级 skeleton；**绝不把 `NoteBrief` 的摘要当编辑器全文**（否则保存会把全文截短且不可恢复）。`VERSION_CONFLICT` → 见 10.5.9。ADMIN 行内编辑他人日志同样走详情（拿 owner 标签），故 `ownerTags` 有值可回显。
- **`NoteEditor` 契约（单受控源；字段只初始化一次；含 `ownerTags?`）**：**props 契约以 §10.5.10 可辨识联合为准**（`{ mode:'create' } & { modelValue; ownerTags?; saving; maxLength?; fieldPermissions?; hasDept } | { mode:'edit' } & { modelValue; note: NoteDetailVO; ownerTags?; saving; maxLength?; fieldPermissions?; hasDept }`——TS 编译期阻止 `mode='edit'` 不传 `note`；`maxLength?` 可选默认 20000；`fieldPermissions?` 编辑态从 `note.canManage` 派生；`hasDept` 必填）。`emits { (e:'save', payload: NoteSavePayload), (e:'cancel'), (e:'draft-change', v: NoteDraft) }`（**`NoteSavePayload` 为可辨识联合：`{ mode:'create'; content; logDate; visibility; tagIds } | { mode:'edit'; content; logDate; visibility; tagIds; version:number }`——创建态**绝不发送 `version`**（`NoteCreateReq` 无 version 字段，避免前端类型为 number 但运行时 undefined/误传）；编辑态必带 `version`。调用方按 `payload.mode` 分派到 `POST /notes` / `PUT /notes/{id}`**。`NoteDraft = { noteId?: number; content: string; logDate: string; visibility: NoteVisibility; tagIds: number[]; version?: number; updatedAt?: string }`——`noteId/version/updatedAt` 改为可选（创建态无 noteId/version；`draft-change` 事件只在 `mode='edit'` 发出，创建态不发 `draft-change`；编辑态 `noteId/version/updatedAt` 必填）。**内容只来自 `modelValue`**（唯一受控源，`v-model` 实时同步）；`note` 仅在 `mode:'edit'` **初始化时读取一次全部可编辑字段**：`content→modelValue 初值 + logDate + visibility + tagIds(= `note.tags.map(t=>t.id)`) + version + ownerTags`（`NoteDetailVO` 只有 `tags: TagVO[]`，无 `tagIds` 字段；初值统一为 `note.tags.map(t=>t.id)`）；5 个需渲染/保存字段 `content/logDate/visibility/tagIds/version` 的初值全部来自 `note`，无一处落到组件默认值）。**note 切换语义**：**区分"同一 note 的异步刷新"（`note` 变化不覆盖）与"切换到另一 note"（必须重置）**——父组件只用 `:key="note.id"` 强制重建编辑器，不使用 `watch(() => note.id, ...)`**——`watch` 会在 note 切换时保留旧组件实例，字段初始化顺序不确定；`:key` 强制重建确保字段初值全部来自新 note。**避免点卡片 A 再点 B 时继续显示 A 内容、保存把 B 覆盖成 A**；仅同一 note 的刷新（version 变化）由父组件控制不换 `:key`，不重置用户输入。**编辑态只接受 `NoteDetailVO`，不接收 `NoteBrief`**（否则 `logDate/visibility/tagIds` 因 `NoteBrief` 字段缺失/口径不同而静默重置，属数据损坏）。**创建态（`mode:'create'`，`note` 为空）默认值由前端先行设定，不依赖后端默认**：`logDate=今天（Asia/Shanghai，本地取日前禁用 `toISOString()`，见 §10.2 log_date 口径）`、`visibility=PRIVATE`、`tagIds=[]`——据此前端可预校验"visibility=INTERNAL 但无部门"（后端 `NOTE_DEPT_REQUIRED/50017` 兜底）；**严禁默认 `visibility=PUBLIC`**（会把工作日志误公开）。**`ownerTags` 来源优先级**：编辑态 `note.ownerTags ?? props.ownerTags`（详情有 owner 标签优先用详情，Admin 编辑他人不回显自己的标签）、创建态只用 `props.ownerTags`（`metaStore.tags`）。**`tagIds` 经 `BbTagPicker` 的 `v-model` 双向绑定**：编辑态初值取 `note.tags.map(t=>t.id)`、创建态 `[]`；`ownerTags/options === undefined`（未加载）才告警并禁用保存（防静默丢标签）；**空数组是合法值**——显示"暂无标签"并允许保存无标签日志；清空全部标签 → `tagIds: []` 照样随 `save` 提交（**不被当作"未修改"**，见 10.5.10 `BbTagPicker` 三态）。`Ctrl+Enter` 保存须判 `isComposing`。
- **标签数据源接入**：note 侧必须显式加载当前用户标签，**不能依赖"先访问任务页才触发 `metaStore.loadTags()`"的隐式链路**。约定：`NoteHomeView`（或 `NoteListView` 的 note 初始化）`onMounted` 调 `metaStore.loadTags()`（已有 `tags.length` 缓存判断，重复调用零成本；也可放 `DefaultLayout` 的 `route.meta.module==='note'` 分支统一加载，避免逐页重复）；`NoteDetailView` 编辑他人日志**优先用详情 `ownerTags`**，详情未返回且 `canEdit` 时再 `loadTags()` 兜底。创建自己的日志/编辑自己日志用 `metaStore.tags`。补 e2e：全新会话直接进 `/notes` 标签下拉有当前用户标签、无标签用户可保存空标签、ADMIN 编辑他人日志回显 owner 标签。

#### 10.5.3 列表筛选/排序/分页/URL 状态规则（`useNoteList`）

- **搜索**：顶部搜索框**回车触发 + 300ms 防抖兜底**；标签筛选**单选**下拉（mine 传 `tagId`、其余 scope 传 `tagName`——`useNoteList` 按当前 scope 决定写哪个字段，见 §5.1）。
- **`date` 与 `start/end` 互斥**：UI 提供"某天"与"范围"两模式互斥 Radio，提交时二选一，后端冲突 400 不与 UI 冲突。
- **排序与置顶筛选（本期提供 UI）**：筛选区加**排序下拉**（`log_date_desc|log_date_asc|created_desc|updated_desc` 四选项，默认 `log_date_desc`）与**"仅置顶"开关**（`pinned=true`）。**scope 切换时 `queryByScope` 为每个 scope 保存独立 query，切回原 scope 时恢复该 scope 的 query（不重置、不清空）**；排序/置顶变化重置 page=1 并写入 URL；移动端折叠进筛选抽屉。
- **分页**：页码分页，**`PAGE_SIZE=20` 集中定义在 `useNoteList`/常量**（不散落），`NoteList` 收敛 `page/total` 并**用 `PAGE_SIZE` 常量算总页数**（**无 `pageSize` prop、无 `size-change` emit**）；**本期固定 20 不提供 pageSize 切换**。**：scope 变化恢复目标 scope 的独立 query（不重置 page=1、不清空筛选）；同 scope 从详情返回保留筛选与页码**；筛选变化重置 page=1。
- **scope 单一事实来源**：**列表页 scope 以 `route.meta.scope` 为唯一来源，URL 不写 `scope`（仅日历页 `/notes/calendar` 例外）**——消除"路由固定 scope / URL `?scope=` / store 缓存"三处并存冲突（访问 `/notes/internal?scope=all` 以路由 internal 为准，忽略 query scope）；`stores/note` 缓存 key 用 **`scope + query`**（不要只存一份全局筛选），同 scope 从详情返回保留、**跨 scope 也保留（各自独立 query，不清空）**。
- **URL 状态**：筛选/排序/页码写入 `query`（`?keyword=&tagId=&tagName=&pinned=&sort=&date=&start=&end=&page=`，**不含 scope**，与 NoteQueryForm/NoteQueryPatch 字段一致），刷新/分享/后退可恢复；components 由 `useNoteList` 统一读 URL 初始化。**分页/筛选全部经 `UseNoteListResult.setPage/setQuery/resetPage` 写入**——`page-change→setPage`、筛选/排序/置顶→`setQuery`（内部重置 page=1）、`refresh()` 不改页码；页面不直接改 `stores/note` 或 `route.query`（完整约定见 §10.1 useNoteList 契约）。

#### 10.5.4 角色 × 字段/按钮 前端矩阵（与后端字段级权限一一对应）

| 能力                              | owner       | EDITOR                        | VIEWER | ADMIN | 归档 owner/admin | 归档 EDITOR          |
| ------------------------------- | ----------- | ----------------------------- | ------ | ----- | -------------- | ------------------ |
| 编辑 content/logDate              | ✅           | ✅                             | —      | ✅     | ✅              | —（`canEdit=false`） |
| visibility 下拉                   | 显示可改        | **禁用/隐藏**                     | —      | 显示可改  | 显示可改           | 禁用                 |
| 标签选择器                           | 可改          | **禁用/隐藏**                     | —      | 可改    | 可改             | 禁用                 |
| 置顶 pin / 归档 archive / 删除 delete | ✅           | **不显示**                       | —      | ✅     | ✅（可取消归档）       | 不显示                |
| 引用"添加"                          | ✅           | ✅                             | —      | ✅     | ✅              | —                  |
| 引用"移除"                          | ✅           | **不显示**                       | —      | ✅     | ✅              | 不显示                |
| 附件上传                            | ✅           | ✅                             | —      | ✅     | ✅              | —                  |
| 附件移除关联                          | ✅           | **不显示**（`canRemoveRef=false`） | —      | ✅     | ✅              | 不显示                |
| `canEdit=false` VIEWER 详情       | 只读详情（不 403） |                               |        |       |                |                    |

> 标注：EDITOR 的 `visibility/标签` = 字段级受限（后端拒改），UI **显示但禁用**并 tooltip"无权修改"；管理类按钮 EDITOR **完全不显示**；引用"添加/移除"分别由后端 `isWritable` 与 `canRemoveRef` 驱动。**归档态 owner/admin 编辑区直接可编辑**（后端允许），无需先取消归档。此矩阵写入 e2e 断言。

#### 10.5.5 授权页交互与状态机按钮矩阵

- **Tab 口径**：**登录用户 3 个 Tab**——"我发起的"(`tab=created`，`created_by=me`) / "我授权的"(`tab=granted`，`grantor_id=me` 全状态；含"待我确认"职责) / "涉及我的"(`tab=incoming`，`grantee_id=me AND created_by<>me`)；**ADMIN 额外第 4 个"全部授权"**(`tab=all`)。非 ADMIN 只看前 3 个，ADMIN 看 4 个（`NoteUserGrantView` 按 `isAdmin` 分支渲染）。
- **Tab 查询**：后端 `GET /note-grants?tab=` 一个端点覆盖四 Tab（不再有独立 `/pending`、`/all`，消解双入口）。**唯一过滤条件见 §5.2 Tab 过滤表**：`created=created_by=me`；`granted=grantor_id=me`（全状态）；`incoming=grantee_id=me AND created_by<>me`（仅 incoming 需排除 S 发起人，created 绝不加）。**SUPERIOR 行（grantor=A、grantee=S、created_by=S、PENDING）**：S 在"我发起的"(created) 出现且**可撤回**（`created_by==me`）；A 在"我授权的"(granted) 出现且**可确认/拒绝/撤回**（`grantor==me`，撤回进入 REVOKED），**批准后变 APPROVED 仍在该 Tab 可撤销/升级（grantor A 始终有入口）**；S 的"涉及我的"不重复。行内 `direction` 仅展示（可能为 `none`），列加"发起人"显示 `createdBy`，二者并存不混。
- **用户选择器**：`BbUserPicker` 授权场景传 **`multiple=false`（单选）**；`mode=SUPERIOR` 传 **`scope='subordinate'`**（数据源限下属）或由组件二次过滤，仍保留服务端校验"仅能选择下属"提示（避免选到非下属提交后才 400/403）。
- **按钮矩阵（按"当前所在 Tab/身份"渲染，`can*` 后端计算前端只读）**：
  | 状态       | 我发起的(created)                                                                                                                                     | 我授权的(granted)                                                                            | 涉及我的(incoming) | ADMIN 全部(all)               |
  | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | -------------- | --------------------------- |
  | PENDING  | 撤回（`created_by==me`）                                                                                                                              | **确认 / 拒绝 / 撤回**（grantor==me；** 允许 grantor 确认前撤回，与"发起人可撤回自己的 PENDING"一致） | —              | 代撤销（PENDING）  |
  | APPROVED | 升级/撤销（grantor==me 时）；**`isAdmin && created_by==me` 可撤销（不可升级，除非 grantor）**；否则只读 | **撤销 / 升级**（grantor==me）；admin 代授权行 grantor A 亦可撤销                            | —              | 代撤销（APPROVED） |
  | REJECTED | 只读                                                                                                                                                | 只读                                                                                       | 只读             | 只读                          |
  | REVOKED  | 只读                                                                                                                                                | 只读（grantor 可看历史）                                                                         | 只读             | 只读                          |
- **确认文案**：撤销 PENDING → "确认撤销该授权？将停止该用户查看权限"；撤销 APPROVED → "撤销后将立即收回该用户当前及未来日志的查看/编辑权限（含历史）"；均为二次确认对话框。
- **ADMIN 代撤销**：ADMIN 在"全部"对 **PENDING/APPROVED 任意行**可代撤销（`canRevoke=true`；REJECTED/REVOKED 已无"撤销"语义，只读）；在"我发起的"(created) 对**本人发起行**（`created_by==me`）——`grantor==me` 可升级/撤销，`isAdmin && created_by==me`（ADMIN 代授权行）可撤销、不可升级（除非 grantor），其余只读；其余 Tab 只读。

#### 10.5.6 引用选择器细节与 backlinks 展示

- `NoteRelationPicker` 入参：`sourceNoteId`（**选择器排除当前 note，防自引用**）、`existingTargets: Array<{targetType,targetId}>`（**已引用项置灰 + "已引用"角标**）、`defaultMode:'NOTE'|'TASK'`（**初始 Tab**）。**组件内部持 NOTE/TASK 两个 Tab（与 §5.5 一致），`mode` 完全为内部状态（`defaultMode` 仅作初始 Tab；`mode` 为内部状态，不做受控 `v-model:mode`）**；`existingTargets` 随当前 Tab 按 `targetType` 过滤（NOTE Tab 只对 NOTE 已引用置灰，TASK Tab 只对 TASK 置灰）。
- **已引用数据来源**：`existingTargets` 由详情页 `relations` 传入（每次搜索不同步返回 `alreadyLinked`），保证与详情一致。
- **搜索结果过滤 archived**：note 目标搜索（scope=visible）返回前过滤 archived；task 目标搜索（`/tasks/search`）后端已按 isVisible 过滤。
- **backlinks（谁引用了我）**：详情页独立区块"来源日志"，展示 `NoteBacklinkVO` 的 `sourceSummary（来源摘要）+ sourceOwnerName（来源作者）+ createdByName（引用人，可空显示"—"）+ createdAt（引用时间）`（来源作者 ≠ 引用人，EDITOR/ADMIN 给他人 note 加引用时引用人为 EDITOR/ADMIN），点击跳 `/notes/{sourceNoteId}` 进入来源详情（不可见来源已整条过滤，不占位）；空态"暂无日志引用这篇日志"。

#### 10.5.7 附件上传体验

- **多文件**：支持多选（`multiple`），逐个上传**并发上限 3**，每个文件独立 `AbortController`；已有 `BbAttachmentList` 单文件 input 升级为多文件。
- **上传 API 签名**：统一封装支持进度与取消——
  ```ts
  interface UploadOptions { onProgress?: (percent: number) => void; signal?: AbortSignal }
  uploadFile(file: File, opts?: UploadOptions): Promise<FileVO>            // note/通用（taskId=null）
  uploadTaskFile(taskId: number, file: File, opts?: UploadOptions): Promise<FileVO>
  ```
  `onProgress` 映射 axios `onUploadProgress`，`signal` 传 axios `signal`；**取消后移除临时进度项、清理半截对象**；`NoteAttachmentList` 暴露 `uploadingList`/`cancel(uploadId)`/`retry(uploadId)`（10.5.10 契约同步）。`uploadId` 由前端在 `upload(file)` 时生成（`crypto.randomUUID()`），上传成功后映射到 `attachmentId`；`cancel/retry` 用 `uploadId` 定位上传队列项（上传中/失败时无 `attachmentId`）；`remove` 仍用 `attachmentId`（仅对已落库附件）。`uploadingList` 结构：`Array<{uploadId:string, file:File, progress:number, status:'uploading'|'failed'|'done', attachmentId?:number}>`。`api/file.ts` 不再返回裸 `Promise<FileVO>` 而无进度/取消入口。
- **限制（单位/空语义）**：`maxSize/allowedExt` **从后端配置下发**。**OpenAPI 明确单位与空语义，避免前端歧义**：`maxSize: number`（**字节**，如 `52428800`，前端换算为可读字符串；不用 `"50MB"` 字符串、不用 `50`(MB) 二义值）、`allowedExt: string[]`（**空数组 `[]` 明确表示"不限制类型"**，禁止被 `Array.every`/`includes` 误判为"拒绝所有"；非空则白名单，如 `['jpg','png']` 无点号）。后端 `application.yml` 默认 `STORAGE_MAX_SIZE:50MB`、`allowed-ext` 留空不限类型；前端仅按下发值做**前置拦截与提示**（对应后端 `40002/40003`），无法下发时按后端默认展示。
- **进度与失败**：上传中显示进度条，可单个取消；失败可重试，"附件已上传但关联失败"提示（不静默）；未保存态附件区禁用 + "保存后可添加附件"。
- **缩略图（生命周期）**：图片经 note 专用下载端点 blob 转 objectURL，**按 attachmentId+size 做内存缓存（组件级 Map），同一附件不重复下载**；`onBeforeUnmount` 统一 `revokeObjectURL`；列表滚动用虚拟滚动按可见项懒加载；`contentType` 为空或非图片（image/*）时**回退文件图标、不创建 objectURL**。列表显示上传者、上传时间。**不引入拖拽上传**（本期范围外）。

#### 10.5.8 `NoteCard` 展示规则

| 元素   | 规则                                                                                                                                                                                                                                                                                                                                                |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 摘要截断 | **后端 `NoteVO.contentSummary` 给固定字符摘要（前 200 码位、保留换行；算法见 §10.4）→ 前端不再 `-webkit-line-clamp`、不自行 `slice`**（`line-clamp` 与 `pre-wrap` 组合在部分浏览器不可靠、且与后端摘要长度双重截断不匹配；**`NoteCard` 取 `note.contentSummary`（`NoteBrief` 无 `content` 全文字段）**）；卡片 `max-height` + `overflow:hidden` + "展开"（进入详情），`white-space: pre-wrap`，禁 `v-html` |
| 标签   | 最多显示 3 个，超出折叠为 "+N"                                                                                                                                                                                                                                                                                                                               |
| 时间   | 显示 `logDate`（`2026-10-20`，非 `updatedAt`）                                                                                                                                                                                                                                                                                                          |
| 标识   | 置顶（置顶图标）、归档（归档徽标+置灰）、visibility 图标（PRIVATE/INTERNAL/PUBLIC）                                                                                                                                                                                                                                                                                       |
| 长内容  | `max-height` + `overflow:hidden` + "展开"进入详情（只显示摘要不内部滚动，超出进入详情），避免 20000 字撑高卡片                                                                                                                                                                                                                                                                                                             |

#### 10.5.9 文案与状态统一清单

- **枚举中文文案**（`utils/i18n` 或常量）：visibility `PRIVATE=私密/INTERNAL=本部门/PUBLIC=公开`；grantRole `VIEWER=可查看/EDITOR=可编辑/OWNER=所有者/ADMIN=管理员`；status `PENDING=待确认/APPROVED=已生效/REJECTED=已拒绝/REVOKED=已撤销`；mode `OWNER=主动授权/SUPERIOR=上级授权/ADMIN=管理员授权`。
- **错误态统一（按业务码分支，不依赖 HTTP status（scope=subordinate 例外））**：后端业务异常一律 HTTP 200 + `ApiResult`，故**页面捕获 `code` 而不是 `error.status`**——**但 `scope=subordinate` 开关关闭是 HTTP 级 403（不返回 `ApiResult`），不走业务码链路**，前端须在此特例下捕获 `err.status===403`。`errorCode.ts` 增加常量：`NOTE_NOT_FOUND(50001)` → **NotFound 空态**"日志不存在或无权访问"；`NOTE_SCOPE_DISABLED(50016)` → **仅作后端日志/审计用，前端不按此 code 分支**（前端改为捕获 `err.status===403 && route.meta.scope==='subordinate'` 渲染空态"下属日志功能未开启，请联系管理员"，不 toast，见 §10.1）；`NOTE_DEPT_REQUIRED(50017)` → 保存时提示"当前账号无部门，无法设为本部门可见"；其余 5xxxx → 按表映射（文案统一"工作日志"）。`fetchAttachment`/详情/列表统一经 `http.ts` 拦截器把 code 映射到 `BizError` 后按此分支（HTTP 403/400 等 HTTP 协议级错误不经 `BizError`，由 `http` 拦截器直接抛 `HttpError`，页面按 `err.status` 分支）。
- **`VERSION_CONFLICT(10006)`（三动作）** → 弹"内容已被他人更新"对话框，明确三个动作：
  1. **查看最新**：只读打开服务端最新版本，**当前编辑器草稿原样保留**（不覆盖、不丢失）；
  2. **以最新为基准重新编辑**：**二次确认**"当前未保存内容将被替换"后再用最新内容载入编辑区；
  3. **保留我的草稿**：把本地草稿**（`NoteDraft`：noteId + content + logDate + visibility + tagIds + version + updatedAt）**复制到剪贴板（或另存为新日志入口）供用户取回——**不只保留正文，冲突前改的日期/可见性/标签一并保留**。

#### 10.5.10 组件最小契约（前端联调基线）

| 组件                                                                         | props                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | emits                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 说明                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NoteListView`                                                             | **`filterConfig?: NoteFilterConfig`**、**`query: NoteQueryForm`（必填：壳渲染 `NoteFilterBar` 所需的当前筛选值，类型为 UI 层 `NoteQueryForm`（非传输层 `NoteQuery`），页面传 `list.query.value`（结构兼容赋值）；所有列表页都有 `list.query.value`，漏传会导致筛选条无当前值，必填可静态检查）**、**`tagOptions?: Array<{id?:number;name:string;count?:number}>`、`tagLoading?: boolean`、`tagError?: string`**、**`notes: NoteBrief[]`、`loading`、`error`、`page`、`total`、`emptyText`、`activeId?: number`**、**`errorMode?: 'error'\|'empty'`（`'error'`=可重试错误态（默认）、`'empty'`=把 error 当空态显示（`NoteSubordinateView` 传 `'empty'` + `emptyText='下属日志功能未开启，请联系管理员'`，不显示重试）；透传给内部 `NoteList`）**；**不接 `title`/`scope` props，标题与 scope 统一读 `route.meta.title`/`route.meta.scope`，避免 props 与 route 双来源** | **显式声明**：`open(note)`、`edit(note)`、`pin(note)`、`archive(note)`、`unarchive(note)`、`delete(note)`、`page-change(page)`、`retry()`、**`change(patch: NoteQueryPatch)`**、**`retry-tags`（透传 `NoteFilterBar.retry-tags` 到页面 `facets.retry()`）**                                                                                                                                                                                                                                                                                                                                                                                                                      | **列表壳（方案 A 壳内渲染）**：**内部渲染 `NoteList`**（把 `notes/loading/error/page/total/emptyText/activeId` props 透传）+ **内部渲染 `NoteFilterBar`**；**`#composer` / `#above-list` / `#editor` 三个具名插槽**（`#filters` 插槽已移除，筛选条由壳内部渲染）；**不接收 `scope`/`archived` props——scope/archived 统一读 `route.meta`**；`#composer`/`#above-list` 由壳内 `v-if="route.meta.scope==='mine' && !route.meta.archived"` 决定渲染                                                                                                                                                                                                           |
| `NoteList`                                                                 | `notes: NoteBrief[]`、`loading`、`error`、`page`、`total`、`emptyText`、**`activeId?: number`（当前选中卡片 id，传递给 NoteCard 高亮；仅视觉、不影响权限/动作）**、**`errorMode?: 'error'\|'empty'`（壳原样透传；`'error'`=可重试错误态（默认）、`'empty'`=把 error 当空态显示不渲染重试按钮；优先级 `loading > error/empty > list`）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | **事件 payload**：`open(note: NoteBrief)`、`edit(note: NoteBrief)`、`pin(note: NoteBrief)`、`archive(note: NoteBrief)`、`unarchive(note: NoteBrief)`（归档态）、`delete(note: NoteBrief)`、`page-change(page: number)`、`retry()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | **纯列表容器**（loading/empty/error/页码分页，props 驱动不发请求，**不含新建框/筛选器、不接收 `scope`**）；**总页数用集中常量 `PAGE_SIZE=20` 计算，即不暴露 `pageSize` prop、不发 `size-change` emit**；payload 使页面 handler 不读组件内部状态                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `NoteFilterBar`                                                            | **`query: NoteQueryForm`、`config: NoteFilterConfig`、`loading?: boolean`、`tagOptions?: Array<{id?:number;name:string;count?:number}>`、`scope: NoteScope`、`tagLoading?: boolean`、`tagError?: string`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | **`change: [patch: NoteQueryPatch]`、`retry-tags`（标签 facet 加载失败时点击重试，页面接 `useNoteFacets.retry()`）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | **共享筛选条**：渲染搜索/标签/排序/置顶/date 各控件，按 `config` 显隐；**内部不直接改 store/URL**——页面只传 `query` + `config` + `tagOptions` + `scope` + `tagLoading/tagError`、`@change="list.setQuery"` + `@retry-tags="facets.retry"`（`setQuery` 内部重置 page=1+写 URL+请求）；`change` 只发出变更字段 patch（非全量 query）；7 个 scope 页复用同一筛选条，杜绝每页复制筛选 UI 或少一个筛选项                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `NoteCard`                                                                 | `note: NoteBrief`、`active`（由 `NoteList` 按 `note.id === activeId` 传入 `:active`，来源已闭合；仅视觉选中高亮、不影响权限/动作）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | **`open(note: NoteBrief)`、`edit(note: NoteBrief)`、`pin(note: NoteBrief)`、`archive(note: NoteBrief)`、`unarchive(note: NoteBrief)`（归档态）、`delete(note: NoteBrief)`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 按钮按 `canEdit/canManage` 且**按 `note.archived` 切换"归档/取消归档"动作**（10.5.4）；EDITOR（`canManage=false`）不显示取消归档。**标题/摘要主体 → `open` 详情**；**显式编辑按钮（`v-if="canEdit"`）→ `edit()`**；`canEdit=false` 时编辑按钮不显示；卡片主体点击不触发 `edit()`（避免"正文点击直接进编辑"与"展开/详情"冲突）；归档 note 的 pin 按钮在 `note.archived` 时隐藏。所有操作按钮（pin/archive/unarchive/delete/edit）**必须 `@click.stop`**（阻止冒泡到卡片主体），否则点击删除会同时触发 `open` 详情，删除确认与跳转叠加；卡片主体只绑定 `open`；补单测"点击 delete 不触发 open"                                                                                                                                                                                                                                           |
| `NoteEditor`                                                               | **可辨识联合 props**：`{ mode:'create' } & { modelValue: string; ownerTags?: TagVO[]; saving: boolean; maxLength?: number; fieldPermissions?: { visibility: boolean; tags: boolean }; hasDept: boolean } \| { mode:'edit' } & { modelValue: string; note: NoteDetailVO; ownerTags?: TagVO[]; saving: boolean; maxLength?: number; fieldPermissions?: { visibility: boolean; tags: boolean }; hasDept: boolean }`（可辨识联合使 TS 编译期阻止 `mode='edit'` 不传 `note`）；**`ownerTags?: TagVO[]`（`undefined`=未加载、`[]`=合法空）**、`saving: boolean`、`maxLength?: number = 20000`（`maxLength` 可选、默认 20000；后端上限固定 20000 码位，前端只允许传更小值（如 5000），不允许覆盖到更大值——若 `maxLength > 20000` 则 clamp 到 20000 并告警**）、**`fieldPermissions?: { visibility: boolean; tags: boolean }`（编辑态从 `note.canManage` 派生——`canManage=false` 时 visibility/tags 禁用（EDITOR 不能改 visibility/tagIds，否则 50013）；创建态全可编辑；未传时不得默认全开**——编辑态 `fieldPermissions = { visibility: note.canManage, tags: note.canManage }`，创建态 `{ visibility: true, tags: true }`；禁止"未传即全开"的不安全默认）**、**`hasDept: boolean`**                                                                                                                                                                                                               | `'update:modelValue': [value: string]`、`save`、`cancel`、`draft-change`（**无 `error(msg)` emit**：NoteEditor 不接触文件，上传由 NoteAttachmentList 拥有，错误经 NoteAttachmentList.error 发出；若编辑器需显示附件错误，由父组件转发）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 新建/编辑共用，`modelValue` 唯一内容源（v-model 双向绑定），编辑态只收 `NoteDetailVO`（10.5.2）。`mode='edit'` 但 `note` 未传时，组件 `onMounted` 抛错并禁用保存按钮（防用户把默认内容保存到目标 note）；或用泛型 props `mode:'create'` 时 `note?: undefined`、`mode:'edit'` 时 `note: NoteDetailVO` 做编译期约束。`NoteDetailVO`/`NoteVO` 无 `tagIds` 字段（只有 `tags: TagVO[]`），编辑态初值用 `note.tags.map(t => t.id)` 而非 `note.tagIds`；`BbTagPicker` 通过 `v-model` 绑定 `modelValue: number[]`（父组件用 `note.tags.map(t=>t.id)` 计算后绑定），**不使用 `initialTagIds` prop**。`maxLength` 默认 20000，`saving=true` 时禁用保存按钮和 Ctrl+Enter，超长时输入框下方显示计数和错误 |
| `NoteAttachmentList`                                                       | `noteId: number`、`canUpload: boolean`、`items: NoteAttachmentVO[]`（**只读，单一方案：父组件持有唯一 `items`，组件**不维护 `localItems`、不本地合并**——上传/绑定成功后发 `changed()` 全量数组并带 `revision`（`revision` 为附件独立版本，不依赖 note.version），父组件仅在 `revision` 更大时替换；`watch(items)` 仅用于父组件替换时更新展示，不触发 `changed()` 回环**）、`maxSize?: number`、`allowedExt?: string[]`（`undefined`=未加载、`[]`=不限制）、**`disabled?: boolean`、`error?: string`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | **emits**：`changed(payload: { items: NoteAttachmentVO[]; revision: number })`——`revision` 由后端在每次 bind/unbind 后递增并随 `NoteAttachmentVO` 列表返回（`POST /notes/{id}/attachments` / `DELETE /notes/{id}/attachments/{aid}` 响应体增加 `revision: number` 字段）；父组件只接受 `revision` 更大的快照，防旧快照覆盖；**不依赖 `note.version`**（附件绑定不 bump note.version，与 note 乐观锁解耦）。、`remove(attachmentId: number)`（仅对已落库附件，父组件调 `DELETE` 成功后替换 `items`）、`error(msg: string)`（上传/绑定/移除失败通知）。`cancel(uploadId: string)`/`retry(uploadId: string)` 是**组件内部方法**（经 `defineExpose({cancel,retry})` 暴露，**不是 emit**）——组件内部上传中/失败项的取消/重试按钮直接调用内部方法，父组件如需控制可经 `ref` 调用。组件**自己拥有上传**——内部调 `uploadFile(file, opts?)` + `POST /notes/{id}/attachments`，不需要 `upload(file)` emit；父组件不负责 `AbortController`。`uploadingList: Array<{uploadId:string, file:File, progress:number, status:'queued'\|'uploading'\|'failed'\|'binding'\|'done', attachmentId?:number}>`（新增 `queued` 状态：并发上限 3 时第 4 个文件排队中；状态机 `queued → uploading → binding → done`（失败回 `failed`）；队列推进：一个 `done/failed` 后从 `queued` 取下一个） |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `NoteRelationPicker`                                                       | `sourceNoteId`、**`existingTargets: ReadonlyArray<NoteTargetRef>`（readonly，父组件用新数组替换，子组件不 mutate）**、`defaultMode:'NOTE'\|'TASK'`、**`pageSize?: number`（默认 20，父组件可控制；`pageSize` 限制 `1..20`，超过 20 截断为 20——后端 `GET /notes/{id}/relations` 与 `GET /tasks/search` 的 `size` 上限均为 20，若父组件传 `pageSize=50` 后端仍只返回 20 条，`loadMore` 的 `total` 与 `page` 会错位导致"还有更多但永远加载不完"；组件内部 `Math.min(pageSize ?? 20, 20)` clamp**）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | **`select(target: NoteTargetRef)`（只发 `NoteTargetRef = {targetType, targetId}`；成功后不 push 到 `relations` 列表，而是追加 `{targetType, targetId}` 到 `existingTargets` 并调用 `useNoteDetail.reload` 拉新 detail）、`close`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | 防抖、分页、已引用置灰、排除自引用、NOTE/TASK 内部 Tab（`defaultMode` 仅初始）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `BbUserPicker`                                                             | `multiple?: boolean`（授权单选传 `false`）、`scope?: 'subordinate'`（SUPERIOR 模式传）、**`visible: boolean`（弹窗显隐，`v-model:visible`）+ `selected: UserVO \| UserVO[]`（已选用户，`v-model:selected`，单选返回单体、多选返回数组；授权表单可回显/清空已选用户）、`loading?: boolean`、`error?: string`**（`visible/selected` 双 v-model；若沿用 `modelValue`/`open` 会与 `defineModel<boolean>` 争用同一 prop，导致 v-model 冲突）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | `'update:visible': [value: boolean]`、`'update:selected': [value: UserVO \| UserVO[] \| undefined]`、`confirm`（确认事件，值经 `selected` 同步）、`cancel`**（不提供 `update:modelValue`/`update:open`）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | 授权页单选 + 可按下属过滤（见 §10.5.5）；表单可绑定 `granteeId/grantorId`，切换模式后状态不丢失                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `BbTagPicker`                                                              | **`options?: TagOption[]`、`modelValue: number[]`（已选 tagIds，多选）、`disabled?: boolean`、`loading?: boolean`、`error?: string`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | **`'update:modelValue': [value: number[]]`、`retry()`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | 标签多选下拉复用（非单选）；**父组件 `v-model` 双向绑定已选值**（`NoteEditor` 绑 `tagIds`，编辑态初值 `note.tags.map(t=>t.id)`/创建态 `[]`）；`options` 未加载（`undefined`+`loading=false`）→ 禁用保存，空数组=合法空，加载失败（`error` 有值）→ 显示错误+重试（三态见 10.5.2）；`options=[]` 显示"暂无标签，可先在任务页创建"                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `BbMiniCalendar`                                                           | `loader?`（**可选：缺省用内置任务默认 loader、忽略 scope**）、`scope`、`height`、`selected`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | `select-date`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | 任务页零改动（不传 loader 即用默认）；note 传自定义 loader；`scope` 变化重取                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `BbFilterRail`                                                             | **无 props，完全 route-driven**：读 `route.meta.module` 决定"任务/工作日志"分组与底部日历跳转目标；折叠状态持久化到 **localStorage key `bb.filter-rail.collapse`**（分组级折叠分 key `bb.filter-rail.<module>.collapsed`）；note 下隐藏任务自定义栏                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | （导航事件走 `router.push`，无需声明 emits）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | 既有共享组件，改动**回归影响任务页**；任务自定义栏隐藏/折叠持久化/模块切换/底部日历按模块跳转 需 4 项任务页回归 e2e                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `BbAttachmentList`                                                         | **`taskId: number`、`files: FileVO[]`、`disabled?: boolean`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | **只发 `changed()`（无 `upload/remove/preview` emit）**（与实际 `BbAttachmentList.vue` 一致——task 专用组件内部调 `uploadTaskFile(taskId, file, opts?)` + blob 下载 + `DELETE /tasks/{taskId}/attachments/{id}`，不需要父组件监听 `upload/remove/preview`）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | 任务专用附件（`uploadTaskFile`/blob 下载/`DELETE /tasks/{taskId}/attachments/{id}`）；删除按钮 `v-if="file.canRemoveFromTask && !disabled"`（后端计算，见 §10.4）；**任务页回归 e2e（上传/下载/删除/disabled 态）**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **`NoteDetailView`** | **无 props**（路由页面从 `route.params.id` 读取 `noteId`，不接收父组件 props；`activeId` 从 `route.query.active` 或 `stores/note.activeNoteId` 读取）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | **无 emits**（路由页面无父组件接收 emits；`back()` 内部调 `router.back()`/`router.push('/notes')`，`edit()` 内部打开行内 `NoteEditor`，`changed()` 内部调 `useNoteDetail.reload`；**`select` 后由 `createRelation` 成功调用 `useNoteDetail.reload()`，不本地 append 到 `detail.relations` 拉新 `NoteDetailVO`（附件与引用都从新 detail 取，消除 changed 与 relations 增量冲突）**）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | **详情页页面容器**：内部用 `useNoteDetail(computed(() => Number(route.params.id)))` 加载 `NoteDetailVO`；状态机 `detail/loading/error/notFound`（不可见/不存在/已删除一律 HTTP 404，`useNoteDetail.notFound` 只认 `HttpError.status===404`（非 `err.response.status`——axios error 已被拦截器转换为 `HttpError`，`err.response` 不保证存在，见 §5.4），不按 `50001` 业务码分支）；**编辑入口内部打开行内 `NoteEditor`（详情页自己持有 `NoteEditor`，不引用 `NoteListView` 的 `#editor` 插槽——详情页是独立路由，不在 `NoteListView` 内）**；附件 `changed()` 后调 `useNoteDetail.reload`；引用：`select` 后统一 `reload()`（不本地 append 到 `detail.relations`）**；`back()` 返回列表时保留 `activeId`/筛选/页码（由 `stores/note` 持久状态保证）；version 冲突经 `useNoteActions()` 处理（§10.5.9）。详情页单测不要求 `activeId` 高亮来源（activeId 只属于 NoteList/NoteCard），仅作为返回列表时的恢复标记                          |
| **`useNoteDetail()`**                        | 入参：`noteId: Ref<number>`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 返回：`{ detail: Ref<NoteDetailVO\|null>, loading: Ref<boolean>, error: Ref<Error\|null>, notFound: Ref<boolean>, reload: Promise<void> }`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 详情加载/竞态取消（`AbortController`，切换 noteId 时 abort 前一个请求）；`reload()` 重新加载（version 冲突后用最新版本覆盖）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **`useNoteFacets(scope)`**                | 入参：`scope: Ref<Exclude<NoteScope,'visible'>>`（facet 端点 `GET /notes/tags?scope=` 只接受 `mine/shared/explore/internal/subordinate/all` 6 个（无 `visible`）；对 `visible` 调 facet 后端会 400，故用 `Exclude<NoteScope,'visible'>` 在类型层面阻止**；响应式，scope 切换自动重新加载）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 返回：`{ tags: ComputedRef<Array<{id?:number;name:string;count?:number}>>, loading: Ref<boolean>, error: Ref<string|null>, retry: Promise<void> }`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | 标签 facet 统一加载：按 scope 调 `GET /notes/tags?scope=`；mine 返回 `{id,name,count}[]`、其余 scope 返回 `{name,count}[]`；loading/error/retry 三态供 `NoteFilterBar` 渲染；内部缓存 60s；scope 切换自动重新加载并 abort 前一个请求；7 页共用
| **`NoteUserGrantView`**       | **无 props**（路由页面从 `route.query.tab`/`route.query.page` 读取，不接收父组件 props；内部用 `useNoteGrants()` composable 自己取数）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | **无 emits**（路由页面无父组件接收 emits；`tab-change`/`page-change`/`approve`/`reject`/`upgrade`/`revoke`/`create()`/`retry()` 均内部处理——写 URL + 调 API + 刷新列表）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | **授权页页面容器**：内部用 `useNoteGrants()` composable 取数（`GET /note-grants?tab=&page=&size=`）；Tab 切换写 URL `?tab=`（刷新/后退可恢复）；**`NoteGrantForm @submit` → 内部 `create()` → 调 `POST /note-grants` → 刷新列表（view 是唯一 API 调用方）**；列表分页（`PageResult<NoteUserGrantVO>`，`page/size` 参数）；loading/empty/error 三态（empty 按 Tab 区分文案，403 非 ADMIN tab=all 单独显示无权限不落入空态）；表格列：授权人/被授权人/角色/状态/模式/发起人/方向/时间/操作；按钮按 `can*` 渲染，与 §10.5.5 按钮矩阵一致                                                                                                                                                                                                                      |
| **`NoteGrantForm`**                                    | `mode: 'OWNER'\|'SUPERIOR'\|'ADMIN'`（`v-model:mode`，`update:mode` 由父级 Tab 决定初始 mode，用户可切换）、`scope?: 'subordinate'`（仅 SUPERIOR 模式必填，OWNER/ADMIN 不需要下属数据源）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `'update:mode': [mode: 'OWNER'\|'SUPERIOR'\|'ADMIN']`、`submit(payload: OwnerGrantPayload \| SuperiorGrantPayload \| AdminGrantPayload)`（三互斥可辨识 payload，提交前按 mode 剔除不该有的字段——OWNER 只 `granteeId+role`、SUPERIOR 只 `grantorId+role`、ADMIN 全 `grantorId+granteeId+role`）、`cancel`。`selectedUser.id → grantorId/granteeId` 映射在表单内部完成。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 三模式字段布局：**OWNER**：grantee picker + role；**SUPERIOR**：grantor picker（scope=subordinate）+ role；**ADMIN**：grantor picker + grantee picker + role。校验：必填、不能自授权（50008）、SUPERIOR 只能选下属（50011）。内部用两个 `BbUserPicker`（ADMIN 模式）或一个（OWNER/SUPERIOR）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **`NoteMiniList`**                                      | `notes: NoteBrief[]`（最多 5 条）、`loading`、`emptyText`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | `open(note: NoteBrief)`、`view-all`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | 右栏"当日日志"专属小列表（固定 5 条、无分页、底部"查看全部"跳转主列表）；不复用 `NoteList`（避免 pageSize=5 与固定 20 冲突）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **`NoteCreatedDraftCard`**                              | `noteId: number`、**`detail?: NoteDetailVO`、`loading?: boolean`、`error?: string`**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | `complete()`、`discard()`、`changed()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | 临时"刚创建"卡片（含附件区/完成/放弃）；从 `#above-list` 插槽渲染；写入 `pendingCreatedNoteId`；7 页/详情返回恢复时复用同一套状态                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |

**`NoteFilterConfig`**——`NoteListView.filterConfig` 的类型（`#filters` 插槽已移除，筛选条由壳内部渲染，显隐配置 prop 名为 `filterConfig`），控制筛选区各控件显隐（仅显隐，不含状态，状态由 `useNoteList` 统一持有）：

```ts
interface NoteFilterConfig {
  search?: boolean;   // 顶部搜索框（默认 true）
  tag?: boolean;      // 列表标签筛选下拉（单选过滤，mine 取 id/name、其余仅 name，见 10.5.3；筛选条不得复用 `BbTagPicker`（`BbTagPicker` 是 `modelValue: number[]` 多选，筛选条需要单选 `el-select` 或 `BbTagPicker` 传 `multiple?: false`——但后端 `tagId/tagName` 只接受单值，UI 允许多选会导致请求 `tagIds[]` 与后端不匹配；筛选条使用独立单选 `el-select` 组件，不复用 `BbTagPicker`）；与编辑器内 BbTagPicker 多选不同）
  sort?: boolean;     // 排序下拉（log_date_desc/asc、created_desc、updated_desc）
  pinned?: boolean;   // "仅置顶"开关
  dateRange?: boolean; // "某天/范围"互斥 Radio
}
```

各 scope 页按需要传入；缺省字段按默认显隐（已在 10.5.3 逐条写清）。

**`NoteQuery` / `NoteQueryForm` / `NoteQueryPatch`**—— 冻结单一事实源方案：

- **`NoteQuery`（传输层类型，由 OpenAPI 生成）**：`pnpm gen:types` 后存在于 `api.d.ts`，对应后端 `NoteQueryDTO`，包含全部查询字段（`scope/keyword/tagId/tagName/visibility/pinned/archived/start/end/date/sort/page/size`，与 NoteQueryForm/NoteQueryPatch 同源）。**前端不再手写 `interface NoteQuery`**。`useNoteList.query: ComputedRef<NoteQuery>` 返回的就是该生成类型。
- **`NoteQueryForm`（UI 层窄化类型，前端手写）**：筛选条 UI 使用的类型，**只含 UI 可编辑字段**，不含 `scope`（由 route.meta 注入）、`page`/`size`（由分页单一入口管理）。定义：

```ts
// frontend/src/types/note.ts
import type { NoteQuery } from '@/types/api';  // 项目生成类型统一在 @/types/api
import type { NoteScope, NoteVisibility } from '@/types/api';

// UI 层窄化：不含 scope/page/size（这三个由 route.meta / 分页入口管理）
// tag 字段拆分为 tagId/tagName——OpenAPI 单个 query 参数只能生成 string，
//   无法表达 number|string 联合；mine 传 tagId(number)、其余 scope 传 tagName(string)，
//   前端按 scope 决定写哪个字段，后端 DTO 同时接收 tagId/tagName 并按 scope 优先级解析。
export type NoteQueryForm = Omit<NoteQuery, 'scope' | 'page' | 'size' | 'tag'> & {
  tagId?: number;    // mine scope 用：选中标签的 id
  tagName?: string;  // 其余 scope 用：选中标签的 name
};

// 筛选条 change 事件 patch：只含本次变更字段，不含 scope/page/size
export type NoteQueryPatch = Omit<Partial<NoteQuery>, 'scope' | 'page' | 'size' | 'tag'> & {
  tagId?: number;
  tagName?: string;
};
```

- **`NoteFilterBar.query: NoteQueryForm`**（UI 层用 `NoteQueryForm`，非 `NoteQuery`）；**`NoteFilterBar.change: [patch: NoteQueryPatch]`**；**`NoteListView.query: NoteQueryForm`**；**`NoteListView.change: [patch: NoteQueryPatch]`**（与 `NoteFilterBar.change` 一致；`change` 是 NoteListView 的第 9 个 emit，纳入事件清单与单测）。
- **传值方式**：页面传 `list.query.value`（解包 `ComputedRef<NoteQuery>`，赋值给 `query: NoteQueryForm` 时 TS 结构兼容——`NoteQuery` 是 `NoteQueryForm` 的超集，可直接赋值）。`useNoteList.setQuery(patch: NoteQueryPatch)` 内部合并到 `queryByScope[scope]`、重置 page=1、写 URL、发请求。

**`NoteQuery` 字段口径**——后端 `NoteQueryDTO` 字段（OpenAPI 生成 `NoteQuery`）：

- `scope?: NoteScope`——由 route.meta 注入，不写入 URL query
- `keyword?: string`
- `tagId?: number`——mine scope 传选中标签的 id（OpenAPI 单参数只能 string，拆为 `tagId`/`tagName`）
- `tagName?: string`——其余 scope 传选中标签的 name
- `visibility?: NoteVisibility`
- `pinned?: boolean`
- `archived?: boolean`——仅 mine 与 ADMIN scope=all 生效；其余 scope 后端忽略
- `start?: string` / `end?: string` / `date?: string`——YYYY-MM-DD；`date` 与 `start/end` 互斥
- `sort?: 'log_date_desc' | 'log_date_asc' | 'created_desc' | 'updated_desc'`
- `page?: number`——1-based
- `size?: number`——默认 PAGE_SIZE=20；右栏 isolated 实例传 5

**`NoteCalendarScope`**——`type NoteCalendarScope = Exclude<NoteScope, 'visible'>`，日历 scope 选择器类型（mine/shared/explore/internal/subordinate/all，6 个，不含 visible）；`useMonthCalendar.scope`、`NoteCalendarView` scope 选择器、`setScope` 入参全程使用该类型；`visible` 无独立日历落地页（仅用于引用选择器搜索与列表查询值）。

**筛选条渲染归属**：`NoteListView` **内部渲染**共享 **`NoteFilterBar`**（壳用 `filterConfig` 配置显隐 + `query` 传入当前值 + `@change` 透传 patch 到页面 `list.setQuery`）——`NoteFilterBar` 是筛选控件的唯一实现，**7 页不再在 `#filters` 插槽中重复实例化**（无 `#filters` 插槽）；与 `NoteListView.filterConfig`（显隐配置）+ `useNoteList.setQuery`（写入契约）三者闭合。各 scope 页的 `NoteFilterConfig` 默认显隐需逐项写死（7 个 scope 的默认配置表：mine 全 true、shared/explore/internal/subordinate 隐藏 pinned、all 隐藏 pinned + 仅 ADMIN 可见、archived 隐藏 dateRange）；未传即全部 true。日期模式从"某天"切到"范围"时，`NoteFilterBar` 发出 `{date: undefined, start: undefined, end: undefined}` 清理旧字段（`setQuery` 支持 `undefined` 删除字段）。`config.search=false` 时不渲染搜索框，`useNoteList` 初始化时忽略/清空 URL 中的 keyword。

#### 10.5.11 前端先行开发约定（mock/类型/e2e 责任边界）

- **MSW 先行**：`frontend/src/mocks/handlers.ts`（note 部分）+ `fixtures.ts` 作为**前端交付物**，由前端维护；后端接口未完成前用 MSW 独立开发联调。note handlers 覆盖 5.1-5.5 全部 note 端点 + task 附件 + `GET /tasks/search`；附件端点用 `HttpResponse.arrayBuffer` 返回二进制并设 `Content-Type`/`Content-Disposition`，**专门造 4 条 blob 用例：200 正常文件、HTTP 403 + JSON body（权限拒绝）、HTTP 401 + JSON body（触发 doRefresh）、200 JSON 10002（续签验证，模拟 refresh 成功/失败两种）**。**完整端点清单**：A.Note CRUD 5 端点（`POST/PUT/DELETE /notes`、`GET /notes/{id}`、`GET /notes`（列表与 B 组同一端点，不重复计数））；B.列表 1 端点（`GET /notes?scope=&tagId=&tagName=&keyword=&archived=&page=&size=`（`tagId`/`tagName` 拆分查询参数））；C.pin/archive/unarchive 4 端点（`POST/DELETE /notes/:id/pin`、`POST /notes/:id/archive`、`POST /notes/:id/unarchive`）；D.note-grants 6 端点（`POST/GET /note-grants`、`POST /note-grants/{id}/approve|reject|upgrade`、`DELETE /note-grants/{id}`）；E.日历 1 端点（`GET /notes/calendar`）；F.note 附件 4 端点（`POST/GET/DELETE /notes/:id/attachments`、`GET /notes/:id/attachments/:aid/download`）；G.files 通用 5 端点（`POST /files`、`GET /files/config`、`GET /files/:id`、`GET /files/:id/preview`、`DELETE /files/:id`）；H.task 附件 4 端点（`POST /tasks/:taskId/attachments`、`GET /tasks/:taskId/attachments/:aid/download|preview`、`DELETE /tasks/:taskId/attachments/:aid`）；I.relations+backlinks 4 端点（`GET/POST/DELETE /notes/:id/relations`、`GET /notes/:id/backlinks`）；J.`GET /tasks/search`；K.`DELETE /tags/:id`；**L.`GET /notes/tags?scope=`**。每端点的 mock 返回结构、错误码、fixture 与 §11.2 接口验收矩阵 A-L 节逐行对齐，避免"5.1-5.5 全部端点"声明与实际 handler 漂移。
- **类型生成过渡**：**先冻结 note schema 到 `openapi.yaml`**，`pnpm gen:types` 生成 `api.d.ts`；冻结前允许在临时 `types/_note.tmp.ts` 手写过渡类型，**冻结后一律以 `gen:types` 为准、删除手写文件**（"禁止 any"指交付态，不阻断先行开发）；类型生成在 CI 中加入校验（schema 变更未重新生成即失败）。
- **e2e 数据**：按**角色矩阵**准备 fixtures——owner / EDITOR / VIEWER / ADMIN / 同部门 / 下属（开关开/关）/ PUBLIC / AUDITOR 八类，覆盖 7 个 scope 与权限分支。
- **任务页回归**：`BbMiniCalendar` 通用化改动纳入现有任务 e2e 回归（默认 loader、高度 280、选中日、月份切换**完全不变**）；note 日历补竞态用例（切月后旧请求迟到不覆盖）。

## 11. 实施计划

### 阶段一：后端核心

- [ ] V6\_\_note.sql（5 表 + 1 视图 + tag(name) 索引）
- [ ] domain + infrastructure Mapper（Note @Version/@TableLogic）
- [ ] NoteCommandService（CRUD + 字段级权限 + log_date + 级联删除 + PATCH + dept_id 刷新）
- [ ] NoteQueryService（7 scope + NoteVisibilitySqlBuilder + 批量装配 + version）
- [ ] NoteAuthService（isVisible + visibleDeptIds + 下属 + 归档 + deleted）
- [ ] NoteUserGrantService（mode + createdBy 视图 + 自动通过记录 approved_by）
- [ ] ErrorCode / LogicDeleteGuard
- [ ] NoteController / NoteUserGrantController

### 阶段二：后端完善 + 前端启动

- [ ] CurrentUserDeptService + DepartmentPolicy.visibleDeptIds（空部门语义）
- [ ] NoteVisibilitySqlBuilder（scope=visible 同源判定）+ **§6.3 的 15 行 Java/SQL 等值测试矩阵**
- [ ] AttachmentAccessService（**实体无关**的 canBind/canReadGeneric/canDelete）+ `AttachmentReferenceGuard`（Note/TaskReferenceGuard 实现）+ FileService（增量绑定/经 guard 双引用保护/FileController 收紧/FieldCleanupJob 改造）
- [ ] **TaskAuthService 新增 `isVisible(Long,Long)` 重载（不可见→404）；TaskAttachmentController（task 专用上传/下载/预览/删除端点 + canRemoveFromTask）+ unbindTaskAttachments 契约（@Transactional + 错误码 50020）+ 返回类型统一（upload→FileVO）+ TaskQueryService/TaskVO.downloadUrl + BbAttachmentList 改造（下载走 axios blob、删除按钮 `canRemoveFromTask && !disabled`）**
- [ ] NoteAttachmentService（关联附件 `isWritable` + assertCanBind + 引用计数 + note 下载 + canUpload/canRemoveRef + **返回 NoteAttachmentVO（含 note 专用 downloadUrl）+ 关联幂等**）
- [ ] NoteRelationService（`isWritable`、添加/backlinks 幂等、自引用、TASK 可见→404、scope=visible 搜索、backlinks 404；**backlinks 返回 NoteBacklinkVO（source 字段 + createdBy/createdByName 引用人）；`note_relation` 写 `created_at/created_by`，EDITOR 添加引用时 `created_by`=EDITOR；NoteRelationVO 增 createdByName**）
- [ ] TagService + TaskTagCleaner + NoteTagCleaner（`cleanByTagId` 事务）
- [ ] SpaRoutes（`/notes`、`/notes/**`）/ 日历接口（7 scope 与列表共用，all 非 admin 403；`scope=all&archived=true` ADMIN 归档；**`internal/visible` 部门树用递归 CTE `dept_id IN (SELECT id FROM dept_tree)`，不做内存并集）
- [ ] openapi.yaml（FileVO downloadUrl/createdBy/canDelete/canRemoveFromTask + NoteAttachmentVO/NoteRelationVO/**NoteBacklinkVO**/NoteDetailVO/NoteBrief 含 canManage + NoteUserGrantVO + `GET /tasks/search` 响应 `{list,total,page,size}` + `UserVO.managerCanReadSubordinate` + **`GET /note-grants` `tab=created|granted|incoming|all`（改用 granted，含 `NoteUserGrantVO.direction:'outgoing'|'incoming'|'none'`） + `GET /files/config`（`maxSize:number` 字节、`allowedExt=[]`=不限制） + `note_relation.created_at/created_by`+ `NoteRelationVO/NoteBacklinkVO.createdByName` + 创建/更新返回 NoteDetailVO** + 50020/50021 错误码 + `isVisible(Long,Long)` 重载；前后端类型同步）
- [ ] 前端基础设施：useNoteList（`UseNoteListOptions {isolated?,cacheKey?,initialQuery?: NoteQueryPatch & {page?,size?}}`，返回契约 `UseNoteListResult {notes,total,page,loading,error,query,setPage,setQuery,resetPage,refresh}`，`page-change→setPage`、筛选→`setQuery`（重置 page=1）；`isolated` 不读写 stores/note 主列表，右栏当日日志用 `{isolated:true, initialQuery:{date:today, page:1, size:5}}` 且为独立轻量列表，不加 pageSize 到 NoteList）+ stores/note.ts（含 `pendingCreatedNoteId`）+ NoteListView（壳内渲染 NoteList + 透传数据/事件含 `unarchive` 与 `activeId`，`#composer`/`#above-list`/`#editor` 插槽，`#filters` 插槽已移除、筛选条由壳内部渲染，读 `route.meta`，方案 A）+ NoteList（纯容器，PAGE_SIZE=20 常量，事件含 `unarchive` emit）+ `useNoteActions()`（`edit()` 拉详情后回调 `onOpenEditor(detail)`，含 `unarchive` 与 `remove`）+ BbTagPicker（`modelValue/update:modelValue` 选中值契约 + `NoteEditor` v-model 绑定 tagIds）+ useMonthCalendar + BbMiniCalendar 通用化（**`loader?` 可选、缺省任务默认 loader**、height CSS 变量、竞态，NoteHome 右栏消费）+ **BbUserPicker 单选/下属** + api（`fetchAttachment` + `uploadFile/uploadTaskFile` 带 onProgress/signal + `fetchFileConfig()`）+ **http.ts 成功拦截器 async（blob+json 解析续签）** + **共享 `NoteFilterBar`（props {query, config, loading?} + emits change(patch)，页面 `@change="list.setQuery"`）** + 左栏 BbFilterRail 两分组（含探索；AUDITOR 按普通用户）+ NoteRelationPicker（内部 Tab + defaultMode）+ **标签源接入 metaStore.loadTags**

### 阶段三：前端完善 + 联调

- [ ] NoteDetailView（含 backlinks 区块（NoteBacklinkVO）/角色×字段按钮矩阵/行内编辑先取详情）/ NoteCalendarView（scope 选择器 + 点日保留 scope + URL 契约）/ 各 scope 页（复用 NoteListView；**壳内渲染 + `#editor` 编辑宿主 + 事件经 useNoteActions 承载（含 unarchive 透传与 onOpenEditor 回调），//**）/ Grants（4 Tab：created/granted/incoming/all，单端点 `tab=`（改用 granted）+ 按钮矩阵 + 二次确认）/ Archived（mine + ADMIN 全部两路由；**owner/admin 经 `unarchive` 取消归档并经壳透传，EDITOR 不显示该按钮）/ NoteAttachmentList（多文件/后端下发限制/进度/取消/重试）/ NoteCard（展示规则）
- [ ] BbFilterRail（任务/工作日志两分组）/ errorCode.ts（5xxxx 全映射 + NOTE_NOT_FOUND 常量，按 code 分支）/ 路由守卫
- [ ] mock + e2e（visible 开关、canBind 403、乐观锁、404、INTERNAL 方向、task 附件 upload/download/preview/delete 端点与上传鉴权、task 附件增量绑定与 note 引用解绑、dept_id 刷新、SUPERIOR 开关、backlinks 404/来源跳转、note 上传后 downloadUrl、note 附件关联幂等与回滚、**附件下载走 blob 携带 Bearer 鉴权：200 文件 / HTTP 403 + JSON body / HTTP 401 + JSON body / 200 JSON 10002 续签重放/失败跳登录 / 中文文件名**（HTTP 403/401 与真实协议一致）、卡片按钮矩阵（owner/EDITOR/VIEWER/ADMIN/归档 EDITOR/归档 owner 六种）、角色×字段按钮矩阵、授权按钮矩阵与二次确认（Tab：created/granted/incoming/all，grantor A 在 granted 确认 SUPERIOR 并可在 APPROVED 后撤销/升级、ADMIN 在 all 代撤销）、探索页 EDITOR grant 可见"添加引用"、右栏当日日志独立实例不污染主列表、临时卡片跨路由恢复与放弃、7 页共用 useNoteActions 无重复调用、行内编辑全文不截断（20 行）、上传进度/取消/3 并发、引用选择器（防抖/已引用禁选/task 搜索/排除自引用/backlinks）、左栏模块分组（含探索；AUDITOR 按普通用户）、日历 scope 切换点日保留 scope + 竞态、任务页 BbMiniCalendar/BbFilterRail/BbAttachmentList 回归（折叠持久化/模块切换/note 下隐藏任务自定义栏/底部日历按模块跳转；附件上传/下载/删除/disabled 态）、归档分段刷新/返回保留、VERSION_CONFLICT 查看最新保留草稿、`log_date` 跨月/月末、新建先保存再传/无标签可保存、ADMIN `scope=all&archived=true` 归档列表、subordinate 403 空态**，fixtures 按八类角色准备）
- [ ] 迁移冒烟 V1→V6 + 视图查询
- [ ] 后端单元测试（权限矩阵、canBind、乐观锁、删除级联、授权状态机、dept_id 刷新、**visible 等值矩阵 15 行、task 附件归属/可见性、SUPERIOR 审计与开关、空部门、FieldCleanupJob 逻辑删除、`AttachmentReferenceGuard` 引用保护、`canRemoveFromTask`、`isVisible(Long,Long)` 404、**`dept_tree` 递归 CTE 与 >500 部门分页/排序/总数/日历一致性、附件下载 blob 鉴权**）
- [ ] 前后端联调
- [ ] **前端组件/composable 单测层 + 接口验收矩阵（见 §11.1/§11.2）**：Vitest + Vue Test Utils 覆盖 **32 个被测对象**（22 组件 + 4 composable + 1 store + 4 api + 1 router，§11.1 A-E 节）逐项断言 emit payload/副作用；接口验收矩阵 **118 行（45 主行 + 73 子行）覆盖 36 个 method+path 端点**（§11.2 A-L 节）的成功/无权限/不可见/业务校验分支


### 阶段四：前端组件/composable 单测 + 接口验收矩阵

> **本阶段为强制要求**：仅靠 e2e 场景无法证明每个 emit payload、每个插槽渲染条件、每个 composable 副作用、每个端点错误分支都正确；必须建立组件/composable 单测层 + 逐接口验收矩阵，作为"所有前端组件、函数、事件与接口测试完成"的证据。

#### 11.1 前端组件/composable 单测清单

**测试框架**：Vitest + Vue Test Utils（**强制，不引入 e2e 兜底降级**）；测试文件放 `frontend/src/**/__tests__/` 或同目录 `*.spec.ts`。每个测试写明初始 URL/store、操作、期望 emit payload/请求体/URL/页面状态。**破坏即失败**：任一断言被实现改动破坏 → 单测失败。

**测试对象清单（22 组件 + 4 composable + 1 store + 4 api + 1 router = 32 项）**：

##### A. 列表壳与容器组件

| 被测对象            | 测试项                                                                                                                                                                                                                                                                                                                                           | 验收（破坏即失败）                                                              |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `NoteListView`  | **10 个 emits（open/edit/pin/archive/unarchive/delete/page-change/retry/change/retry-tags，与 §10.5.10 契约表 9+1 emits 一致）逐项转发**；`change(patch: NoteQueryPatch)` patch 不含 scope/page/size；`retry-tags` 触发 `useNoteFacets.retry()`；`#composer`/`#above-list` 仅 `route.meta.scope==='mine' && !archived` 渲染；`#editor` 正常渲染；**壳内渲染 `NoteFilterBar`（无 `#filters` 插槽）**；`activeId` 透传给内部 `NoteList`                                                                                         | 删除任一事件转发（含 change/retry-tags）→ 单测失败；`change` patch 含 scope/page/size → 失败；`retry-tags` 未触发 facets.retry → 失败；`activeId` 未透传 → 失败                                    |
| `NoteList`      | `activeId` 传给 `NoteCard`（`:active="note.id===activeId"`）；分页 `page-change(page)`；空/错/加载三态；`PAGE_SIZE=20` 总页数计算；**8 个 emits（open/edit/pin/archive/unarchive/delete/page-change/retry）逐项转发**                                                                                      | 父传 `activeId=2` → `NoteCard.active=true` 对应行；任一 emit 转发缺失（含 retry）→ 失败 |
| `NoteCard`      | owner/EDITOR/VIEWER/ADMIN/归档 EDITOR/归档 owner 六身份按钮矩阵；`archive`/`unarchive` 按 `note.archived` 切换；**6 个 emits逐项 payload**：`open(note)` 点击卡片主体触发、`edit(note)` 编辑按钮、`pin(note)` 置顶、`archive(note)` 归档、`unarchive(note)` 取消归档、`delete(note)` 删除；`active` 样式 | 归档态显示"取消归档"而非"归档"；任一 emit payload 错（含 open/unarchive）→ 失败              |
| `NoteFilterBar` | `config` 显隐各控件；**`change` 发出 `NoteQueryPatch` patch，只含变更字段、非全量、不含 scope/page/size**；`loading` 禁用态；`query` 接收 plain object（非 `ComputedRef`）                                                                                                                                                                                                                  | 发全量 query 而非 patch → 单测失败；patch 含 scope/page/size → 单测失败                                              |

##### B. 编辑器与附件/引用组件

| 被测对象                 | 测试项                                                                                                                                                                                                                                                                              | 验收（破坏即失败）                                                                  |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `NoteEditor`         | 编辑态字段初始化（5 字段来自 `note`）；`NoteSavePayload` 可辨识联合；**创建态 `save` payload 不含 `version`**；`ownerTags`/`options` 三态（`undefined` 禁用/`[]` 可保存/有值回显）；`Ctrl+Enter` 与 `isComposing`；**`cancel`/`draft-change` emits**                                                       | 创建态误加 `version` → 类型测试/单测失败；`cancel` 不发出 → 失败                              |
| `NoteAttachmentList` | `changed/remove/error` payload（**不含 `upload`**——组件自己拥有上传，内部调 `uploadFile + POST`，无 `upload(file)` emit）；**`remove` 使用 `attachmentId`（非关联 `id`）**；`cancel(uploadId)/retry(uploadId)` 是组件内部方法（`defineExpose`，非 emit）；关联失败回滚提示经 `error` emit | `remove` 误传关联 `id` → 单测失败；断言"组件内部调用 `uploadFile + POST`"而非"父组件监听 `upload`" |
| `NoteRelationPicker` | NOTE/TASK 内部 Tab；`existingTargets` 按 `targetType` 过滤；防抖；已引用禁选；`select`/`close` payload；**排除自引用**                                                                                                                                                                                 | NOTE Tab 不对 TASK 已引用置灰 → 失败                                                |

##### C. 共享 bb 组件

| 被测对象                                 | 测试项                                                                                                                                                                                                                                                                           | 验收（破坏即失败）                                                                 |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `BbTagPicker`                        | `v-model` 回显（预选 2 个）；清空为 `[]`；`disabled` 禁用；`options===undefined` 禁用保存；`options===[]` 可保存（合法空）；**多选（非单选）**                                                                                                                                                                    | `undefined` 时仍允许保存 → 失败；单选模式 → 失败                                         |
| `BbMiniCalendar`                     | 默认 loader（不传 `loader`）；`scope` 变化重取；旧响应迟到不覆盖；`selected` 外部变化不强制切月；**`select-date` emitpayload 为 `date: string\|null`（形如 `YYYY-MM-DD`，与正文 §10.1 第 862 行 `emits: { (e:'select-date', date: string\|null): void }` 一致，不再用 `{date,count}` 对象）** | 旧月份响应覆盖新月份 → 失败；`select-date` 不发出 → 失败；payload 类型不符 → 失败                  |
| `BbUserPicker`     | `multiple` 单选/多选；`scope='subordinate'` 过滤下属；**`confirm` emit payload（单选 UserVO/多选 UserVO[]）**；空选禁用 confirm                                                                                                                                                                    | 多选误返回单对象 → 失败；`scope='subordinate'` 不过滤 → 失败                              |
| `BbFilterRail`     | 读 `route.meta.module`（`'task'`/`'note'`）切换分组；note 分组含"探索"；折叠状态读写 localStorage key `bb.filter-rail.<module>.collapsed`；底部日历按模块跳转；**AUDITOR 与普通用户一致显示 note 分组**                                                                                                                 | `module='note'` 时显示任务分组 → 失败；折叠状态不持久化 → 失败                                |
| `BbAttachmentList` | **只发 `changed()`（无 `upload/remove/preview` emit）**（与实际组件一致）；**task 模式上传走 `uploadTaskFile`、note 模式走 `uploadFile`**；`disabled` 禁用态；删除按钮 `v-if="file.canRemoveFromTask && !disabled"`（task 模式）；blob 下载（非 `<a href>` 直连）                    | task 模式误走 `uploadFile` → 失败；删除按钮用 `canDelete` 而非 `canRemoveFromTask` → 失败；断言 `changed()` 而非 `upload/remove/preview` → 失败 |

##### D. 页面视图（10 个）

| 被测对象                       | 测试项                                                                                                                                                                                                                                                                                      | 验收（破坏即失败）                                                  |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `NoteHomeView`（mine 列表页）   | 组装 `NoteListView` + `NoteEditor` + `NoteFilterBar`；读 `route.meta.scope='mine'`；`#composer`/`#above-list` 渲染（非归档）；保存后重拉第一页（不本地插入）；编辑器与列表状态联动                                                                                                                                              | 保存后本地插入而非重拉 → 失败；`scope` 从 URL query 读而非 `route.meta` → 失败 |
| `NoteExploreView`（探索页）     | `scope='explore'`；`#composer` 不渲染（非 mine）；引用来源文案"来自 X 的日志"；按可见性过滤                                                                                                                                                                                                                        | `#composer` 误渲染 → 失败                                       |
| `NoteSharedView`（分享页）      | `scope='shared'`；`#composer` 不渲染；列表**只出现 `grantee_id=me AND status=APPROVED` 的 grantor 日志**（§3.3 第 253 行：`shared` = 被授权给我的）；**`grantor_id=me`（我授权出去的）日志不得出现**（否则越权混入自己日志）                                                                                                                | 误显示新建入口 → 失败；出现 `grantor_id=me` 的日志 → 失败                   |
| `NoteInternalView`（部门内页）   | `scope='internal'`；`#composer` 不渲染；按 `dept_id ∈ visibleDeptIds(me)` 过滤                                                                                                                                                                                                                   | 过滤用 `dept_id==me.deptId` 而非 `visibleDeptIds` → 失败          |
| `NoteSubordinateView`（下属页） | `scope='subordinate'`；`#composer` 不渲染；开关关闭 403 空态渲染（"下属日志功能未开启"）                                                                                                                                                                                                                         | 开关关闭时显示列表而非空态 → 失败                                         |
| `NoteAllView`（ADMIN 全部页）   | `scope='all'`；**仅 ADMIN 路由守卫**；`#composer` 不渲染；支持 `archived=true` 查全部归档                                                                                                                                                                                                                  | 非 ADMIN 进入 → 路由守卫失败；`archived=true` 不生效 → 失败               |
| `NoteUserGrantView`（授权页）   | 4 Tab（created/granted/incoming/all）；`tab=all` 仅 ADMIN；状态机按钮矩阵（PENDING/APPROVED/REJECTED/REVOKED）；二次确认；ADMIN 代授权在我发起的可见                                                                                                                                                                   | `tab=all` 非 ADMIN 不 403 → 失败                               |
| `NoteCalendarView`（日历页）    | **UI 选择器只暴露 6 scope**（mine/shared/explore/internal/subordinate/all，§10.1 第 877 行：日历不提供 visible 聚合入口）；`scope=all` 非 ADMIN 403；`scope=subordinate` 关闭 403；点日跳列表保留 scope；年月切换竞态取消（日历 UI 不暴露 `scope=visible`，该行为由 `useMonthCalendar`/`api/calendar.ts` 测试覆盖） | `scope=visible` 出现在 UI 选择器 → 失败                            |
| `NoteArchivedView`（归档页）    | `/notes/archived=mine+archived`；ADMIN 再加 `/notes/archived/all=scope=all&archived=true`；分段即两路由，返回/刷新天然保留；恢复归档后列表更新                                                                                                                                                                        | 归档分段用 query 而非两路由 → 失败                                     |
| `NoteDetailView`（详情页）      | `GET /notes/{id}`；**不可见 → HTTP 404 → notFound 空态**；编辑入口按 `canEdit`；管理按钮按 `canManage`；附件/引用/backlinks 区展开（`activeId` 属 `NoteList` 职责，本页不测）                                                                                                                                                                               | 不可见时显示空对象而非 404 -> 失败；按 50001 断言而非 404 -> 失败                                       |

##### E. composable / store / api / router

| 被测对象                                                                        | 测试项                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 验收（破坏即失败）                                                                                                 |
| --------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `useNoteList`                                                               | `setPage(page)` 写 URL `?page=` + 请求 + 保留页码；`setQuery(patch)` **重置 `page=1`** + 写 URL + 请求；`resetPage()` 重置 page=1 不请求；`refresh()` 不改页码；`isolated:true` 不读写 `stores/note` 主列表；竞态取消（旧响应不覆盖新）；scope 以 `route.meta.scope` 为唯一来源                                                                                                                                                                                                                                             | `setQuery` 去 `page=1` → 单测失败；`isolated:true` 仍写 store → 失败                                                |
| `useNoteActions()`                                                            | `edit()` 拉详情后调用 `ctx.onOpenEditor(detail)`（payload 为 `NoteDetailVO`）；`remove` 映射组件 `delete` 事件；`unarchive` 分支（`note.archived` 时调 unarchive API）；失败回滚 + toast；`ctx.onChanged` 回调在 CRUD 后触发                                                                                                                                                                                                                                                                               | `edit()` 不调 `onOpenEditor` → 失败；`onChanged` 不触发 → 失败                                                        |
| **`useNoteFacets`** | scope 变化重新加载（`Exclude<NoteScope,'visible'>`）；loading/error/retry 三态；60s 缓存命中不重复请求；scope 切换 abort 前一个请求；非 mine facet 无 id 不报 TS 错误 | scope 变化不重载 → 失败；60s 内重复请求 → 失败；scope 切换旧响应覆盖新 → 失败；传入 `visible` scope → TS 编译失败 |
| `useMonthCalendar` | `MonthCalendarState` 完整契约：`{year, month, loading, error, days: Array<{date,count}>, selected: string\|null, scope, setScope(s), prevMonth, nextMonth, reload, select(date)}`（**与正文一致，不再用 `{dates,load}` 简写**）；`setScope(s)` 切 scope→重取数并重置到当月；`prevMonth/nextMonth` 月份切换重取（竞态取消，旧响应不覆盖新）；`reload()` 不改 scope/year/month 重新取数；`select(date)` 设 selected；`selected` 从外部变化仅设日期、不强制切月；`loader` 工厂注入                                                               | `setScope` 不触发重取 → 失败；`prevMonth/nextMonth` 不重取 → 失败；`reload()` 误改 scope → 失败；旧响应覆盖新 → 失败                   |
| `stores/note`                                             | 缓存 key = `scope + query`（非全局单份）；**同 scope 从详情返回保留、跨 scope 也保留（各自独立 query，不清空）**；`activeNoteId` 状态；`setQuery` 不丢失未变字段                                                                                                                                                                                                                                                                                                                                                                         | **跨 scope 清空 → 失败**；缓存 key 只用 scope 不含 query → 失败                                                            |
| `api/note.ts`                                             | `listNotes(query)` → `GET /notes` + query 序列化（scope/tagId/tagName/keyword/visibility/pinned/archived/start/end/date/sort/page/size，`tagId`/`tagName` 拆分）；`getNote(id)` → `GET /notes/{id}`；`createNote(payload)` → `POST /notes`（创建态无 version）；`updateNote(payload)` → `PUT /notes/{id}`（含 version）；`deleteNote(id)`；`pinNote/archiveNote/unarchiveNote`；`listGrants(tab)`；`createGrant/approve/reject/upgrade/deleteGrant`；`listRelations/backlinks`；`createRelation/deleteRelation`                  | 创建态误发 version → 失败；query 序列化漏字段 → 失败                                                                      |
| `api/file.ts`                                             | `fetchAttachment(url)`：blob 下载统一入口，`http.get<Blob>` + `responseType:'blob'` + `timeout:0`；`stripApiPrefix()` 兜底（防后端误带 `/api/v1`）；断言 `resp.data instanceof Blob`；从 `Content-Disposition filename*` 提取文件名（中文编码）；**不二次 `JSON.parse`**（解析由 `http.ts` 拦截器完成）；`uploadFile(file, opts?)` / `uploadTaskFile(taskId, file, opts?)`（`opts={onProgress,signal}`）；`fetchFileConfig()` → `GET /files/config`                                                                         | `fetchAttachment` 二次 `JSON.parse` → 失败；`uploadFile` 误带 taskId → 失败                                        |
| `api/http.ts`                                             | 成功拦截器 async——blob 响应按 `X-Api-Result: 1` + `JSON.parse` 成功 + 结构匹配 ApiResult schema 三条件全满足才解析（防合法 .json 附件误判），否则原样返回 Blob；`code===10002/20005` 复用 `doRefresh()` + `_retried` 重放原请求；刷新失败 → `redirectToLogin()`；其余非 0 code → `throw new BizError(code, message, traceId)`；**新增 `HttpError` 类**：错误拦截器对 HTTP 401/403/400/404 抛 `HttpError(status,data?,message,traceId?)`（非 BizError），页面读 `err.status`；**401 刷新条件收窄**：仅 401 且 code∈{10002,20005} 才刷新，其余 401 直接 redirectToLogin；**普通 JSON 10002 统一处理**：无论 responseType 是 json 还是 blob，code===10002/20005 都复用 doRefresh 重放；**错误拦截器也处理 Blob**：`error.response.config.responseType==='blob'` 且 `data instanceof Blob` 时先 `await data.text` 解析再按 HTTP status 分流                                                                                                                                                                                            | blob 响应不解析 `ApiResult` → 失败；`_retried` 标记丢失导致无限重试 → 失败                                                    |
| `api/calendar.ts`                      | `fetchCalendar({year,month,scope})` → `GET /notes/calendar?year=&month=&scope=`；URL/参数序列化（year/month 数字、scope 字符串）；**`scope=visible` 开关关闭→静默省略下属分支（200，不 403，§5.3 第 485 行；此行为在 api 层测试，不在 NoteCalendarView UI 测试）**；**`scope=subordinate` 关闭→HTTP 403，`err.status===403`，不解析 50016**；`scope=all` 非 ADMIN→403；非法 year/month→400；响应 `[{date,count}]` 结构断言；竞态取消（旧响应不覆盖新） | `scope=visible` 开关关闭时 403 → 失败；`scope=subordinate` 关闭时按 50016 断言 → 失败（返回 403）；参数序列化漏字段 → 失败；响应结构不符 → 失败 |
| `router/routes.ts` + `router/index.ts` | note 页 children 相对路径 + `meta.module:'note'` + `meta.scope` 单一来源 + `meta.archived` + **`RouteMeta` 声明 `module?:'task'\|'note'`/`scope?:NoteScope`/`archived?:boolean`（routes.ts 显式声明 title/requiresAuth/roles 与 route.meta.scope）**；`/notes/calendar` 支持 `scope/year/month/date` query；`/notes/archived/all`（ADMIN 归档，`roles:['ADMIN']`）；路由守卫：`scope=all` 非 ADMIN -> `router.replace({ path:'/403', query:{ from: to.fullPath } })`（保留 from 供登录后回跳）                                                                                                                                                                                                                                             | `meta.scope` 缺失 -> 失败；非 ADMIN 进入 `/notes/all` 不守卫 -> 失败；403 不保留 from -> 失败                                                     |

> **强制**：Vitest + Vue Test Utils 为**阶段四强制测试框架**，不引入 e2e 兜底降级。若某项确实无法单测覆盖（如跨页路由跳转），则在 §11.2 接口验收矩阵的对应端点行标注 `e2e` 并在 e2e 场景清单中建立可定位断言，**不能只写"7 页共用"聚合描述**。

#### 11.2 接口验收矩阵

**格式**：`方法 + 路径 + 角色/前置 fixture + 请求 + 期望 HTTP 状态 + 期望业务码 + 期望响应关键字段 + 测试类型（单测/e2e）`。每行分配固定 fixture（用户 u_owner/u_editor/u_viewer/u_admin/u_dept/u_sub/u_public/u_auditor + 部门/授权/note/attachment/relation）。

**HTTP 状态 / 业务码分列说明**：后端业务异常与认证失败均 `ApiResult.fail(...)` 且 HttpStatus 恒为 `200`（由全局异常处理器统一映射），但 **HTTP 协议级错误**（401 未认证 / 403 权限拒绝 / 400 参数格式 / 404 路由不存在 / 405 方法不允许）走标准 HTTP 状态码、**不进入业务码**。故矩阵将"期望 HTTP 状态"与"期望业务码"分列：

- **期望 HTTP 状态**：`200`（业务成功或业务失败包在 `ApiResult.fail`）/ `400`（参数格式/路由参数）/ `401`（未登录，跳登录）/ `403`（权限拒绝，非业务码）/ `404`（路由不存在或 404 语义化）/ `409`（冲突）。
- **期望业务码**：仅 HTTP=200 时有意义，`0`=业务成功，`50001/50006/50007/50008/50013/50014/50017/50020/10002/10006` 为业务失败断言目标。

断言目标错误码汇总：50001（NOTE_NOT_FOUND）/ 50002（NOTE_CONTENT_TOO_LONG）/ 50003（NOTE_TAG_INVALID）/ 50006（NOTE_RELATION_TARGET_NOT_FOUND）/ 50007（NOTE_GRANT_DUPLICATE）/ 50008（NOTE_GRANT_SELF）/ 50012（NOTE_LOG_DATE_INVALID，未来超 7 天）/ 50013（NOTE_FIELD_FORBIDDEN）/ 50014（NOTE_GRANT_FLOW_FORBIDDEN）/ 50017（NOTE_DEPT_REQUIRED）/ 50020（TASK_ATTACHMENT_NOT_LINKED）/ 10002（未登录）/ 10006（VERSION_CONFLICT）。1c=50002、1d-2=50012、1e=50003 已补精确码；50018 不再出现（同一附件允许多 note 引用）。**仅作内部语义、不在矩阵断言**：50015（NOTE_GRANT_MODE_INVALID，HTTP 400）/ 50016（NOTE_SCOPE_DISABLED，HTTP 403；前端捕获 `err.status===403` 而非 code，见 §10.1/§10.5.9）/ 50018（NOTE_ATTACHMENT_CANNOT_BIND，HTTP 403，权限层）/ 50019（NOTE_ATTACHMENT_BOUND，task 场景）/ 50021（TASK_ATTACHMENT_FORBIDDEN，HTTP 400）/ 50004（NOTE_ATTACHMENT_INVALID，内部校验）/ 50005（NOTE_ATTACHMENT_NOT_LINKED，内部校验）/ 50009（NOTE_GRANT_NOT_FOUND，404 语义）/ 50010（NOTE_GRANT_NOT_PENDING，归入 50014 流程）/ 50011（NOTE_GRANT_NOT_SUBORDINATE，HTTP 403）。

##### A. Note CRUD

| #    | 方法     | 路径            | 角色/前置                                                 | 请求                                                        | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                                                                                                                                                                                                                                        | 测试类型 |
| ---- | ------ | ------------- | ----------------------------------------------------- | --------------------------------------------------------- | ------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1    | POST   | `/notes`      | owner                                                 | `{content,logDate,visibility:PRIVATE,tagIds:[]}`          | 200     | 0     | `NoteDetailVO{id,version,contentSummary,ownerTags,attachments,relations}`                                                                                                                                                                                                                                                                                       | e2e  |
| 1a   | POST   | `/notes`      | 未登录                                                   | —                                                         | 401     | —     | 跳登录（不返回 `ApiResult`）                                                                                                                                                                                                                                                                                                                                            | e2e  |
| 1b   | POST   | `/notes`      | 登录（任何登录用户含 EDITOR 均可创建自己的 note，§5.1 第 338 行"创建权限=登录"） | `{content:"...",logDate:今天,visibility:PRIVATE,tagIds:[]}` | 200     | 0     | `NoteDetailVO`                                                                                                                                                                                                  | e2e  |
| 1c   | POST   | `/notes`      | 登录                                                    | `content` 超 20000 字符                                      | 200     | 50002 | `NOTE_CONTENT_TOO_LONG`（内容上限校验失败，精确码 50002）                                                                                                                                                                                                                                                                      | e2e  |
| 1d   | POST   | `/notes`      | 登录                                                    | `logDate:"2026-13-15"`（ISO 形状但月份非法）                       | 400     | —     | 参数格式错误（标准 Spring `@DateTimeFormat(iso=ISO.DATE)` 使用严格解析，`LocalDate.parse("2026-02-30")`/`"2026-13-15"` 均抛 `DateTimeParseException` → 400，不进入 Service 层 50012；`2026-02-30` 属不可解析，非业务非法）                                                                                                                                 | e2e  |
| 1d-1 | POST   | `/notes`      | 登录                                                    | `logDate:"abc"`（非 ISO 字符串）                                | 400     | —     | 参数格式错误                                                                                                                                                                                                                                                                             | e2e  |
| 1d-2 | POST   | `/notes`      | 登录                                                    | `logDate:"2026-12-31"`（可解析但未来超 7 天）                       | 200     | 50012 | `NOTE_LOG_DATE_INVALID`                                                                                                                                                                                                                                                | e2e  |
| 1e   | POST   | `/notes`      | 登录                                                    | `tagIds:[他人标签id]`                                         | 200     | 50003 | `NOTE_TAG_INVALID`（标签归属校验失败，标签非创建者所有，精确码 50003）                                                                                                                                                                                                                                                                  | e2e  |
| 2    | POST   | `/notes`      | 登录                                                    | `visibility:INTERNAL` 但 owner 无部门                         | 200     | 50017 | `NOTE_DEPT_REQUIRED`                                                                                                                                                                                                                                                                                                                                            | e2e  |
| 4    | PUT    | `/notes/{id}` | owner，version 旧                                       | `{...version:旧}`                                          | 200     | 10006 | `VERSION_CONFLICT`（提示查看最新，保留草稿）                                                                                                                                                                                                                                                                                                                                 | e2e  |
| 4a   | PUT    | `/notes/{id}` | owner，version 正确                                      | `{content:"新内容",version:当前}`                              | 200     | 0     | `NoteDetailVO{content,version(递增)}`                                                                                                                                                                                                                                                                                               | e2e  |
| 5    | PUT    | `/notes/{id}` | EDITOR 改 visibility                                   | `{visibility:PUBLIC}`                                     | 200     | 50013 | `NOTE_FIELD_FORBIDDEN`（EDITOR 字段级受限）                                                                                                                                                                                                                                                                                                                            | e2e  |
| 5a   | PUT    | `/notes/{id}` | VIEWER（canEdit=false）                                 | `{content:"..."}`                                         | 403     | —     | 权限拒绝                                                                                                                                                                                                                                                                                                                                                            | e2e  |
| 6    | GET    | `/notes/{id}` | 不可见用户                                                 | —                                                         | 404     | —     | 404 不返回 body code                                                                                                                                                                                                                                                                                                                                 | e2e  |
| 6a   | GET    | `/notes/{id}` | owner                                                 | —                                                         | 200     | 0     | `NoteDetailVO{content,ownerTags,canEdit,canManage}`                                                                                                                                                                                                                                                                                                             | e2e  |
| 7    | DELETE | `/notes/{id}` | EDITOR（canManage=false）                               | —                                                         | 403     | —     | 权限拒绝（EDITOR 不可删除）                                                                                                                                                                                                                                                                                                                                               | e2e  |
| 7a   | DELETE | `/notes/{id}` | owner                                                 | —                                                         | 200     | 0     | 软删除+级联（`note_tag`/`note_attachment_ref`/`note_relation`（source+target 两侧）清理 + `UPDATE note SET deleted=1`，§3.4 第 270 行；**不清理 `note_user_grant`**——属用户级全量授权，删单条 note 撤销会导致该 grantor 对所有其他日志的授权一并失效，属严重授权数据破坏）；**附加断言**：删除后 `note_user_grant` 中该 grantor 的 APPROVED 行**仍存在**，grantee 仍可访问该 grantor 的其他 note（验证不误级联） | e2e  |
| 7b   | DELETE | `/notes/{id}` | 不可见用户                                                 | —                                                         | 404     | —     | 404 不返回 body code                                                                                                                                                                                                                                                                                                                                        | e2e  |

##### B. Note 列表与 scope

| #   | 方法  | 路径                                  | 角色/前置   | 请求                   | 期望 HTTP | 期望业务码          | 期望响应关键字段                                                                                                                                                      | 测试类型 |
| --- | --- | ----------------------------------- | ------- | -------------------- | ------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 8   | GET | `/notes?scope=all`                  | 非 ADMIN | —                    | 403     | —              | 权限拒绝（非业务码；50016 仅用于 `scope=subordinate` 开关关闭，见 §8 错误码表；与 §5.3 行 484 / §7 行 953 一致）                                                                            | e2e  |
| 8a  | GET | `/notes?scope=all`                  | ADMIN   | —                    | 200     | 0              | 全部列表（含他人归档可选）                                                                                                                                                 | e2e  |
| 9   | GET | `/notes?date=&start=&end=`          | 登录      | date 与 start/end 同时传 | 400     | —              | 参数冲突（不进入业务码）                                                                                                                                                  | e2e  |
| 10  | GET | `/notes?size=200`                   | 登录      | size>100             | 200     | 0              | 截断 100，`total/page/size=100`                                                                                                                                  | e2e  |
| 11  | GET | `/notes?scope=mine&archived=true`   | owner   | —                    | 200     | 0              | 归档列表（仅 mine archived 生效）                                                                                                                                      | e2e  |
| 12  | GET | `/notes?scope=shared&archived=true` | 登录      | 非 mine archived      | 200     | 0（忽略 archived） | 非归档列表（archived=true 仅 mine 有意义）                                                                                                                               | e2e  |
| 12a | GET | `/notes?scope=subordinate`          | 开关关闭    | —                    | 403     | —              | 权限拒绝 | e2e  |
| 12b | GET | `/notes?scope=internal`             | 同部门用户   | —                    | 200     | 0              | 部门内可见列表（含 `dept_id ∈ visibleDeptIds(me)`）                                                                                                                     | e2e  |
| 12c | GET | `/notes?scope=visible`              | 开关关闭    | —                    | 200     | 0              | 静默省略下属分支（不 403，与 subordinate 不同）                                                                                                                              | e2e  |
| 12d | GET | `/notes?tagName=未知名` | 登录 | tagName 不存在 | 200 | 0 | 空列表（不报错，查询参数为 `?tagName=`） | e2e |
| 12e | GET | `/notes?tagId=999999` | 登录 | tagId 不存在 | 200 | 0 | 空列表（不报错） | e2e |
| 12f | GET | `/notes?tagId=abc`                     | 登录      | tagId 非法              | 400     | —              | 参数错误                                                                                                                                                      | e2e  |
| 12g | GET | `/notes?keyword=测试`                 | 登录      | keyword 模糊匹配         | 200     | 0              | 匹配 content/contentSummary 含关键词                                                                                                                                | e2e  |

##### C. Note pin / archive / unarchive

| #   | 方法     | 路径                      | 角色/前置                   | 请求 | 期望 HTTP | 期望业务码 | 期望响应关键字段                  | 测试类型 |
| --- | ------ | ----------------------- | ----------------------- | -- | ------- | ----- | ------------------------- | ---- |
| 13  | POST   | `/notes/{id}/pin`       | owner                   | —  | 200     | 0     | `pinned=true`，列表置顶        | e2e  |
| 13a | POST   | `/notes/{id}/pin`       | EDITOR（canManage=false） | —  | 403     | —     | 权限拒绝（pin 属管理权）            | e2e  |
| 13b | POST   | `/notes/{id}/pin`       | 不可见用户                   | —  | 404     | —     | 404（不可见一律 404，不泄露存在性）          | e2e  |
| 14  | DELETE | `/notes/{id}/pin`       | owner                   | —  | 200     | 0     | `pinned=false`            | e2e  |
| 14a | DELETE | `/notes/{id}/pin`       | EDITOR                  | —  | 403     | —     | 权限拒绝                      | e2e  |
| 15  | POST   | `/notes/{id}/archive`   | owner                   | —  | 200     | 0     | `archived=true`，列表隐藏（可取消） | e2e  |
| 15a | POST   | `/notes/{id}/archive`   | EDITOR                  | —  | 403     | —     | 权限拒绝（archive 属管理权）        | e2e  |
| 16  | POST   | `/notes/{id}/unarchive` | owner                   | —  | 200     | 0     | `archived=false`，恢复列表     | e2e  |
| 16a | POST   | `/notes/{id}/unarchive` | EDITOR                  | —  | 403     | —     | 权限拒绝                      | e2e  |

##### D. 授权 note-grants

| #   | 方法     | 路径                          | 角色/前置                             | 请求    | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                                                                | 测试类型 |
| --- | ------ | --------------------------- | --------------------------------- | ----- | ------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 17  | POST   | `/note-grants`              | OWNER mode，granteeId=me           | 自授权   | 200     | 50008 | `NOTE_GRANT_SELF`                                                                                                                                                                       | e2e  |
| 18  | POST   | `/note-grants`              | SUPERIOR mode，body 带 granteeId    | —     | 400     | —     | 参数格式错误 | e2e  |
| 19  | POST   | `/note-grants`              | grantor 已有 5 条 PENDING            | 第 6 条 | 400     | —     | 参数校验失败（每 grantor 最多 5 PENDING）                                                                                                                                                          | e2e  |
| 20  | POST   | `/note-grants`              | 重复 active grant                   | —     | 200     | 50007 | `NOTE_GRANT_DUPLICATE`                                                                                                                                                                  | e2e  |
| 21  | POST   | `/note-grants/{id}/approve` | 非 grantor                         | —     | 403     | —     | 权限拒绝（仅 grantor 可 approve）                                                                                                                                                               | e2e  |
| 22  | POST   | `/note-grants/{id}/approve` | grantor，状态非 PENDING               | —     | 200     | 50014 | `NOTE_GRANT_FLOW_FORBIDDEN`                                                                                                                                                             | e2e  |
| 22a | POST   | `/note-grants/{id}/reject`  | grantor，状态 PENDING                | —     | 200     | 0     | `status=REJECTED`                                                                                                                                                                       | e2e  |
| 22b | POST   | `/note-grants/{id}/reject`  | grantor，状态非 PENDING               | —     | 200     | 50014 | `NOTE_GRANT_FLOW_FORBIDDEN`                                                                                                                                                             | e2e  |
| 22c | POST   | `/note-grants/{id}/upgrade` | grantor，VIEWER→EDITOR，状态 APPROVED | —     | 200     | 0     | `role=EDITOR`                                                                                                                                                                           | e2e  |
| 22d | POST   | `/note-grants/{id}/upgrade` | grantor，状态非 APPROVED              | —     | 200     | 50014 | `NOTE_GRANT_FLOW_FORBIDDEN`                                                                                                                                                             | e2e  |
| 23  | DELETE | `/note-grants/{id}`         | created_by=me 撤 APPROVED          | —     | 403     | —     | 权限拒绝（撤 APPROVED 仅 grantor/ADMIN）                                                                                                                                                        | e2e  |
| 23a | DELETE | `/note-grants/{id}`         | created_by=me 撤 PENDING           | —     | 200     | 0     | `status=REVOKED`（发起人可撤自己 PENDING）                                                                                                                                             | e2e  |
| 23b | DELETE | `/note-grants/{id}`         | grantor 撤 APPROVED                | —     | 200     | 0     | `status=REVOKED`                                                                                                                                                                        | e2e  |
| 23c | DELETE | `/note-grants/{id}`         | ADMIN 撤任意                         | —     | 200     | 0     | `status=REVOKED`（ADMIN 代撤销，approved_by 记录）                                                                                                                                              | e2e  |
| 24  | GET    | `/note-grants?tab=all`      | 非 ADMIN                           | —     | 403     | —     | 权限拒绝（tab=all 仅 ADMIN）                                                                                                                                                                   | e2e  |
| 24a | GET    | `/note-grants?tab=created`  | 登录                                | —     | 200     | 0     | `created_by=me` 全状态列表（**含 SUPERIOR 行 S 发起的 `created_by=S`、ADMIN 代授权行 `created_by=ADMIN`**，§5.2 第 468 行；**非 `grantor_id=me`**，否则漏 SUPERIOR/ADMIN 代授权）                                    | e2e  |
| 24b | GET    | `/note-grants?tab=granted`  | 登录                                | —     | 200     | 0     | `grantor_id=me` 全状态列表（**我作为授权方**，§5.2 第 468 行；**与 `tab=created`（`created_by=me`）是不同维度**：created 含 SUPERIOR/ADMIN 代授权行，granted 不含）                    | e2e  |
| 24c | GET    | `/note-grants?tab=incoming` | 登录                                | —     | 200     | 0     | `grantee_id=me AND created_by<>me` 列表                                                                                                                                                   | e2e  |
| 24d | GET    | `/note-grants?tab=all`      | ADMIN                             | —     | 200     | 0     | 全部授权列表                                                                                                                                                  | e2e  |

##### E. 日历

| #   | 方法  | 路径                                              | 角色/前置   | 请求            | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                        | 测试类型 |
| --- | --- | ----------------------------------------------- | ------- | ------------- | ------- | ----- | ------------------------------------------------------------------------------- | ---- |
| 25  | GET | `/notes/calendar?scope=subordinate`             | 开关关闭    | —             | 403     | —     | 权限拒绝 | e2e  |
| 25a | GET | `/notes/calendar?scope=all`                     | 非 ADMIN | —             | 403     | —     | 权限拒绝（与列表一致）                                                                     | e2e  |
| 26  | GET | `/notes/calendar?year=abc`                      | 登录      | 非法 year/month | 400     | —     | 参数格式错误                                                                          | e2e  |
| 26a | GET | `/notes/calendar?year=2026&month=10&scope=mine` | owner   | —             | 200     | 0     | `[{date,count}]` 按 log_date 聚合                                                  | e2e  |

##### F. Note 附件

| #   | 方法     | 路径                                       | 角色/前置                   | 请求                  | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 测试类型 |
| --- | ------ | ---------------------------------------- | ----------------------- | ------------------- | ------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---- |
| 27  | POST   | `/notes/{id}/attachments`                | 不可见用户                   | `{attachmentId:1}`  | 404     | —     | 404 不返回 body code                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | e2e  |
| 27a | POST   | `/notes/{id}/attachments`                | EDITOR，note 已归档         | `{attachmentId:1}`  | 200     | 50013 | `NOTE_FIELD_FORBIDDEN`（归档时 EDITOR 不可写）                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | e2e  |
| 27b | POST   | `/notes/{id}/attachments`                | owner                   | `{attachmentId:1}`  | 200     | 0     | `NoteAttachmentVO{attachmentId,createdAt}`                                                                                                                                                                                                                                                                                                                                                                                                                                                             | e2e  |
| 27c | POST   | `/notes/{id}/attachments`                | 可见用户，附件创建者≠me 且非 ADMIN  | `{attachmentId:他人}` | 403     | —     | `assertCanBind` 失败（HTTP 级权限拒绝；附件创建者≠me 且非 ADMIN → 403）。①同一附件**允许被多个 note 引用**（`note_attachment_ref` 唯一约束为 `UNIQUE(note_id, attachment_id)`，不禁止一附件多 note）；②`assertCanBind` 不看引用（§12 #22 / §6.5），只校验创建者/ADMIN；③ 不存在"附件已绑他人 note → 50018"规则：`assertCanBind` 不看引用，`note_attachment_ref` 允许一附件多 note；④ 50018=`NOTE_ATTACHMENT_CANNOT_BIND`（权限层，但 HTTP 走 403 不进业务码），50019=`NOTE_ATTACHMENT_BOUND`（task 绑定场景，非 note） | e2e  |
| 28  | GET    | `/notes/{id}/attachments`                | 不可见用户                   | —                   | 404     | —     | 404 不返回 body code                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | e2e  |
| 28a | GET    | `/notes/{id}/attachments`                | 可见用户                    | —                   | 200     | 0     | `NoteAttachmentVO[]`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | e2e  |
| 29  | GET    | `/notes/{id}/attachments/{aid}/download` | 不可见用户                   | —                   | 404     | —     | 404 不返回 body code                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | e2e  |
| 29a | GET    | `/notes/{id}/attachments/{aid}/download` | 可见用户                    | —                   | 200     | 0     | blob 二进制（200 文件 / **HTTP 403 + JSON body** / **HTTP 401 + JSON body** / 200 JSON 10002 续签，见 §5.4）                                                                                                                                                                                                                                                                                                                                                                                                                                             | e2e  |
| 30  | DELETE | `/notes/{id}/attachments/{aid}`          | EDITOR（canManage=false） | —                   | 403     | —     | 权限拒绝（移除属管理权）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | e2e  |
| 30a | DELETE | `/notes/{id}/attachments/{aid}`          | owner                   | —                   | 200     | 0     | 解除关联（`canRemoveRef`，有 task 引用只解 note 关联不物理删）                                                                                                                                                                                                                                                                                                                                                                                                                                                           | e2e  |

##### G. Files 通用端点

| #   | 方法     | 路径                    | 角色/前置            | 请求                  | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                                                                             | 测试类型 |
| --- | ------ | --------------------- | ---------------- | ------------------- | ------- | ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 31  | POST   | `/files`              | 登录，body 带 taskId | `{taskId:1,...}`    | 400     | —     | 参数格式错误（通用 /files 端点禁 taskId 属请求格式错误→400，不进入业务码；task 附件须走专用端点 POST /tasks/{taskId}/attachments（Row 35）；§8 的 50021 仅作为同类场景的语义标识，HTTP 层走 400） | e2e  |
| 31a | POST   | `/files`              | 登录，taskId=null   | `{taskId:null,...}` | 200     | 0     | `FileVO{id,downloadUrl,createdBy,canDelete,canRemoveFromTask}`                                                                                                                                       | e2e  |
| 32  | GET    | `/files/config`       | 登录               | —                   | 200     | 0     | `{maxSize:number(字节), allowedExt:string[]（空=不限制）}`                                                                                                                                                   | e2e  |
| 33  | GET    | `/files/{id}`         | 非创建者非 ADMIN      | —                   | 403     | —     | 权限拒绝（`canReadGeneric` 仅创建者/ADMIN）                                                                                                                                                                    | e2e  |
| 33a | GET    | `/files/{id}`         | 创建者              | —                   | 200     | 0     | `FileVO`                                                                                                                                                                                             | e2e  |
| 33b | GET    | `/files/{id}/preview` | 非创建者非 ADMIN      | —                   | 403     | —     | 权限拒绝                                                                                                                                                                                                 | e2e  |
| 33c | GET    | `/files/{id}/preview` | 创建者              | —                   | 200     | 0     | blob 二进制（预览）                                                                                                                                                                                         | e2e  |
| 34  | DELETE | `/files/{id}`         | 有 note+task 双引用  | —                   | 409     | —     | 拒绝物理删除（提示先解除关联）；文件行/磁盘文件/note_task 关联均不变（与 §5.4 规则 6 "任一被引用→禁止物理删除"一致；task 侧解绑走专用端点 `DELETE /tasks/{taskId}/attachments/{id}`，不经此端点）                                                                 | e2e  |
| 34a | DELETE | `/files/{id}`         | 无引用，创建者          | —                   | 200     | 0     | 物理删行+删盘                                                                                                                                                                                              | e2e  |
| 34b | DELETE | `/files/{id}`         | 无引用，非创建者非 ADMIN  | —                   | 403     | —     | `canDelete=false`（权限拒绝）                                                                                                                                                                              | e2e  |

##### H. Task 附件端点

| #   | 方法     | 路径                                           | 角色/前置                                                    | 请求                    | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                             | 测试类型 |
| --- | ------ | -------------------------------------------- | -------------------------------------------------------- | --------------------- | ------- | ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 35  | POST   | `/tasks/{taskId}/attachments`                | task 可写（owner/assignee/ADMIN）                            | `multipart/form-data` | 200     | 0     | `FileVO{id,downloadUrl,task_id}`                                                                                                                     | e2e  |
| 35a | POST   | `/tasks/{taskId}/attachments`                | 不可写 task（CC/无关）                                          | —                     | 403     | —     | `assertWritable` 失败                                                                                                                                  | e2e  |
| 35b | POST   | `/tasks/{taskId}/attachments`                | 不可见 task                                                 | —                     | 404     | —     | 404 不泄露存在性（`isVisible` 失败）                                                                                                                           | e2e  |
| 36  | GET    | `/tasks/{taskId}/attachments/{aid}/download` | 可见 task，task_id 匹配                                       | —                     | 200     | 0     | blob 二进制（task 专用路径）                                                                                                                                  | e2e  |
| 36a | GET    | `/tasks/{taskId}/attachments/{aid}/download` | 不可见 task                                                 | —                     | 404     | —     | 404（`isVisible` 失败，不泄露）                                                                                                                              | e2e  |
| 36b | GET    | `/tasks/{taskId}/attachments/{aid}/download` | task_id 不匹配                                              | —                     | 404     | —     | 404（`attachment.task_id != taskId`，不泄露）                                                                                                              | e2e  |
| 36c | GET    | `/tasks/{taskId}/attachments/{aid}/preview`  | 可见 task                                                  | —                     | 200     | 0     | blob 二进制（预览）                                                                                                                                         | e2e  |
| 36d | GET    | `/tasks/{taskId}/attachments/{aid}/preview`  | 不可见 task                                                 | —                     | 404     | —     | 404                                                                                                                                                  | e2e  |
| 37  | DELETE | `/tasks/{taskId}/attachments/{aid}`          | task 附件、**有 note 引用**、assertWritable + canRemoveFromTask | —                     | 200     | 0     | `attachment.task_id` 置 null、`note_attachment_ref` 保留、磁盘文件/文件行保留（§5.4 规则 2 `unbindTaskAttachments` 分支）                                                | e2e  |
| 37a | DELETE | `/tasks/{taskId}/attachments/{aid}`          | task 附件、**无 note 引用**、assertWritable + canRemoveFromTask | —                     | 200     | 0     | 物理删行 + 删盘                                                                                                                                            | e2e  |
| 37b | DELETE | `/tasks/{taskId}/attachments/{aid}`          | 不可写 task（非 owner/assignee/ADMIN）                         | —                     | 403     | —     | `assertWritable` 失败（**不进入 `canRemoveFromTask` 判定**，§5.4 规则 5）                                                                                        | e2e  |
| 37c | DELETE | `/tasks/{taskId}/attachments/{aid}`          | 可写 task 但非上传者非 ADMIN                                     | —                     | 403     | —     | `canRemoveFromTask=false`（§5.4 规则 7）                                                                                                                 | e2e  |
| 37d | DELETE | `/tasks/{taskId}/attachments/{aid}`          | `attachment.task_id != taskId`                           | —                     | 404     | —     | 404 不泄露存在性 | e2e  |

##### I. 引用与 backlinks

| #   | 方法     | 路径                                   | 角色/前置           | 请求                                 | 期望 HTTP | 期望业务码 | 期望响应关键字段                                           | 测试类型 |
| --- | ------ | ------------------------------------ | --------------- | ---------------------------------- | ------- | ----- | -------------------------------------------------- | ---- |
| 38  | POST   | `/notes/{id}/relations`              | 自引用             | `{targetType:NOTE,targetId:id}`    | 400     | —     | 自引用拒绝（参数校验）                                        | e2e  |
| 39  | POST   | `/notes/{id}/relations`              | 目标 NOTE 不可见     | `{targetType:NOTE,targetId:他人不可见}` | 200     | 50006 | `NOTE_RELATION_TARGET_NOT_FOUND`                   | e2e  |
| 39a | POST   | `/notes/{id}/relations`              | owner，正常引用 task | `{targetType:TASK,targetId:1}`     | 200     | 0     | `NoteRelationVO{id,targetType,targetId,createdAt}` | e2e  |
| 39b | POST   | `/notes/{id}/relations`              | 重复引用同一目标        | —                                  | 200     | 0     | 幂等（不报错，返回已存在 relation）                             | e2e  |
| 39c | POST   | `/notes/{id}/relations`              | EDITOR，note 已归档 | —                                  | 200     | 50013 | `NOTE_FIELD_FORBIDDEN`（归档时 EDITOR 不可写）             | e2e  |
| 40  | GET    | `/notes/{id}/relations`              | 可见用户            | —                                  | 200     | 0     | `NoteRelationVO[]`（我引用了谁，按可见性过滤目标）                 | e2e  |
| 40a | GET    | `/notes/{id}/relations`              | 不可见用户           | —                                  | 404     | —     | 404 不返回 body code                                   | e2e  |
| 41  | GET    | `/notes/{id}/backlinks`              | 不可见用户           | —                                  | 404     | —     | 404 不返回 body code                                        | e2e  |
| 41a | GET    | `/notes/{id}/backlinks`              | 可见用户            | —                                  | 200     | 0     | `NoteBacklinkVO[]`（谁引用了我，过滤不可见来源）                  | e2e  |
| 42  | DELETE | `/notes/{id}/relations/{relationId}` | EDITOR（非 owner） | —                                  | 403     | —     | 权限拒绝（移除引用属 owner/ADMIN）                            | e2e  |
| 42a | DELETE | `/notes/{id}/relations/{relationId}` | owner           | —                                  | 200     | 0     | 解除引用                                               | e2e  |

##### J. Task 搜索（note→TASK 引用选择器）

| #   | 方法  | 路径                           | 角色/前置 | 请求            | 期望 HTTP | 期望业务码 | 期望响应关键字段                         | 测试类型 |
| --- | --- | ---------------------------- | ----- | ------------- | ------- | ----- | -------------------------------- | ---- |
| 43  | GET | `/tasks/search?keyword=a`    | 登录    | keyword <2 字符 | 200     | 0     | 空列表（`{list:[],total:0}`）         | e2e  |
| 44  | GET | `/tasks/search?keyword=Task` | 登录    | 大小写不敏感        | 200     | 0     | 匹配 task/Task                     | e2e  |
| 44a | GET | `/tasks/search?keyword=`     | 登录    | 空 keyword     | 200     | 0     | 空列表                              | e2e  |
| 44b | GET | `/tasks/search?keyword=Task` | 未登录   | —             | 401     | —     | 跳登录 | e2e  |

##### K. Tag 清理（事务）

| #   | 方法     | 路径           | 角色/前置                  | 请求 | 期望 HTTP | 期望业务码 | 期望响应关键字段                                     | 测试类型 |
| --- | ------ | ------------ | ---------------------- | -- | ------- | ----- | -------------------------------------------- | ---- |
| 45  | DELETE | `/tags/{id}` | 有 note_tag+task_tag 引用 | —  | 200     | 0     | `TagUsageCleaner` 同时清两表，事务回滚验证               | 单测   |
| 45a | DELETE | `/tags/{id}` | 无引用                    | —  | 200     | 0     | 直接删除 tag                                     | 单测   |
| 45b | DELETE | `/tags/{id}` | tag 不存在                | —  | 404     | —     | tag 不存在         | e2e  |
| 45c | DELETE | `/tags/{id}` | 非管理员                   | —  | 403     | —     | 权限拒绝（tag 删除属管理权，403 分支） | e2e  |

##### L. Note 标签 facet

| #   | 方法  | 路径                      | 角色/前置           | 请求                  | 期望 HTTP | 期望业务码 | 期望响应关键字段                                        | 测试类型 |
| --- | --- | ----------------------- | --------------- | ------------------- | ------- | ----- | ---------------------------------------------- | ---- |
| 46  | GET | `/notes/tags?scope=mine`   | 登录（mine）        | scope=mine           | 200     | 0     | `{id,name,count}[]`（mine 含 id） | e2e  |
| 46a | GET | `/notes/tags?scope=shared` | 登录（shared）     | scope=shared         | 200     | 0     | `{name,count}[]`（非 mine 无 id）                  | e2e  |
| 46b | GET | `/notes/tags?scope=mine`   | 登录（mine 无标签）  | scope=mine，用户无标签    | 200     | 0     | `[]`（空数组，非 404）                              | e2e  |
| 46c | GET | `/notes/tags?scope=all`    | 非 ADMIN          | scope=all            | 403     | —     | 权限拒绝（scope=all 仅 ADMIN） | e2e  |
| 46d | GET | `/notes/tags?scope=xxx`    | 登录               | scope=非法值           | 400     | —     | 参数校验失败（scope 枚举外）                       | e2e  |

> **验收**：矩阵共 **45 主行 + 73 子行 = 118 行**覆盖 **36 个 method+path 端点**（A Note CRUD 5 端点（含 `GET /notes` 列表）/ B 列表 1 端点（与 A 的 `GET /notes` 同一端点，不重复计入 36）/ C pin+archive+unarchive 4 端点 / D note-grants 6 端点 / E 日历 1 端点 / F note 附件 4 端点 / G files 通用 5 端点 / H task 附件 4 端点 / I relations+backlinks 4 端点 / J tasks/search 1 端点 / K tags 1 端点 / **L notes/tags facet 1 端点**）。**分支覆盖策略**：**关键端点**（Note CRUD / note-grants / note 附件 / task 附件 / files 删除）覆盖**成功 + 主权限失败（403/404，按端点定义）+ 主业务失败（500xx/409，按端点定义）三类必备分支**——并非所有组都同时有 403 和 500xx（如 `GET /notes/{id}` 无 403、files 删除主业务失败是 409 非 500xx），而是按端点实际语义覆盖其主失败分支；**401 未认证分支不由每个端点独立覆盖**，而是**由全局鉴权测试组统一覆盖**（即全局拦截器对所有非白名单端点统一返回 401，无需为每个端点重复造 401 用例；矩阵仅在代表性端点如 `POST /notes` 1a、`GET /tasks/search` 44b 保留 401 断言作为全局鉴权契约的回归点）；**其余端点**（列表 / 日历 / tasks/search / tags / files config / **notes/tags facet**）至少覆盖**成功 + 主失败分支**（如 403/400/500xx/409 任一；facet 覆盖成功+空数组+scope 非法 403+非 ADMIN 调 scope=all 403）。已补最低分支：`PUT /notes/{id}` owner 成功（4a）、`POST /notes` content/log_date/tagIds 校验（1c/d/e）、`GET /tasks/search` 401（44b）、`DELETE /tags/{id}` 404/403（45b/c）、`GET /note-grants?tab=all` ADMIN 成功（24d）。HTTP 状态与业务码分列，断言不混淆；附件/关系/backlinks 的 404 分支均有独立断言（不返回空数组）；`/files/config`、通用 `/files`、`/tasks/search`、task 专用附件 4 端点、**notes/tags facet** 均有 mock + e2e/单测。测试报告按矩阵输出通过/失败，不能只写"全场景已覆盖"。


## 12. 决策结论

| # | 事项 | 结论 |
| --- | --- | --- |
| 1 | 标签关联表 | `note_tag` + `v_tag_relation` |
| 2 | 归档语义 | 归档隐藏可取消；删除逻辑删除不可恢复 + 级联 |
| 3 | 分享功能 | 删公开分享，改 `note_user_grant` |
| 4 | 旧数据 | 全新部署，V6 只建表 |
| 5 | 下属可见 | 开关即授权；`manager-can-read-subordinate` 开则上级读下属 |
| 6 | note↔task 引用 | 本期只做 note 侧引用 task |
| 7 | 授权默认值 | status 默认 PENDING + `uq_nug_active` + mode |
| 8 | EDITOR 权限 | 默认 VIEWER；EDITOR 字段级受限 |
| 9 | 归档检查 | owner/admin 后立即判断 |
| 10 | dept_id 来源 | `CurrentUserDeptService.deptIdOf`，禁 UserContext.deptId |
| 11 | 日志日期 | `log_date`，一天多条 |
| 12 | 授权页 | 本期交付 |
| 13 | 文件访问 | file 模块仅保留**实体无关**判定（canBind/canReadGeneric/canDelete，均创建者/ADMIN）；note/task 可见性由各自模块调用前校验；引用保护经公共接口 `AttachmentReferenceGuard` |
| 14 | 附件生命周期 | note 引用保护 + deleteByTask 解绑 + 孤儿 24h |
| 15 | 列表 archived/deleted | 非 mine 显式过滤；archived=true 仅 mine |
| 16 | ADMIN all | `scope=all`（仅 ADMIN） |
| 17 | 标签过滤 | 跨用户 scope 按 tag.name |
| 18 | PRIVATE | 方案 A |
| 19 | INTERNAL 方向 | 方案 2：`note.dept_id ∈ visibleDeptIds(me)`，列表与 isVisible 同源 |
| 20 | ADMIN 代授权 | 直接授权（mode=ADMIN）+ 审计 + approved_by 记录 |
| 21 | 通知 | 本期不做红点；审计 + 授权页可见 |
| 22 | 附件绑定归属 | `assertCanBind`（创建者/ADMIN），**不看可见性/引用** |
| 23 | visible 行为 | 与 isVisible 同源；开关关静默省略下属分支 |
| 24 | 未定行为清零 | 全文无"可选/可加/或/至少"残留；subordinate 关→403、非法 visibility 忽略、非 mine archived 忽略、date/size/tag 取值确定 |
| 25 | 日历命名 | **通用化改造** `BbMiniCalendar`，不新建 BbCalendar；抽出 `useMonthCalendar` 暴露 `MonthCalendarState` 契约，note/任务共享同一实现 |
| 26 | NoteVO.version | 加入 |
| 27 | dept_id 刷新 | 仅在"转为 INTERNAL"时刷新为 owner 当前部门；已存在 INTERNAL 保持快照 |
| 28 | owner 标签 | `ownerTags` 仅 canEdit/canManage 返回；列表不返回 |
| 29 | /files 权限 | 最小权限：通用只创建者/ADMIN；**新增 task 专用附件端点**，note/task 走各自专用端点 |
| 30 | task 附件端点 | 新增 task 专用 `download\|preview` 端点，**落点 `TaskAttachmentController`（类级 `/api/v1/tasks`）**，校验 task 归属 + `isVisible(Long,Long)`（新增重载），404 兜底；下载一律 axios blob（不 `<a href>` 直连）；task 删除按钮按后端 **`canRemoveFromTask`**（=`assertWritable && (createdBy==me \|\| isAdmin)`） |
| 31 | 调岗可见性 | 已存在 INTERNAL 保持原快照（历史留档）——原部门同事在 owner 调岗后仍可见旧日志，这是有意的历史留档 |
| 32 | visible 语义 | `visible = isVisible && archived=0`；建 **15 行** Java/SQL 等值测试矩阵（grant EDITOR、deleted=1 含 owner、空 visibleDeptIds 拆行）；admin visible 与 all 结果集相同；空 visibleDeptIds 禁 `IN ()` |
| 33 | task 附件更新链路 | **task PUT 不做全量替换**：`fileIds==null` 与 `[]` 同义均**不动附件**；附件增删走独立入口；`bindToTask` 增量绑定（新增校验 canBind、已属本 task no-op、其他 task 409） |
| 34 | SUPERIOR 与开关 | `mode=SUPERIOR` 是显式授权，**不受 `managerCanReadSubordinate` 开关约束**；开关只控自动可见 |
| 35 | task 附件可删范围 | **本期仅上传者/ADMIN 可删**；`FileVO.canDelete`=可否物理删（创建者/ADMIN，**不含引用保护**），task 删除按钮用 **`canRemoveFromTask`**（`assertWritable && (createdBy==me \|\| isAdmin)`）；task owner 删除他人附件不在本期范围（未来项见 §13） |
| 36 | task 附件删除链路 | **task 专用删除端点 `DELETE /tasks/{taskId}/attachments/{id}`**（`TaskAttachmentController`，权限 **`assertWritable + canRemoveFromTask`**（统一，`canRemoveFromTask`=`assertWritable && (createdBy==me \|\| isAdmin)` 已含 assertWritable），**不再用 `canDelete`**（`canDelete` 仅用于 `DELETE /files/{id}` 通用物理删除前置））→`unbindTaskAttachments`（有 note 引用只解 task_id，无引用物理删）；`/files/{id}` 保留通用/物理删除 |
| 37 | note 关联删除权限 | `FileVO.canDelete` 收窄为"可物理删"；note 关联删除由 **`NoteAttachmentVO.canRemoveRef`（owner/ADMIN）** 控制，`NoteAttachmentList` 不用 `FileVO.canDelete`；上传后以 `POST /notes/{id}/attachments` 返回的 `NoteAttachmentVO`（含 note 专用 downloadUrl）为准 |
| 38 | task 附件上传入口 | **上传统一走 task 专用端点 `POST /tasks/{taskId}/attachments`**（`TaskAttachmentController`，`assertWritable` 在 task 侧）；**通用 `POST /files` 拒绝 `taskId`（非空→400，`TASK_ATTACHMENT_FORBIDDEN`），只允许 null**；`FileService.upload` 只落盘返回 `FileVO`，不依赖 `TaskAuthService` |
| 39 | 返回类型与错误码 | `FileService.upload`/`FileController.upload`/`TaskAttachmentController` 上传返回 `FileVO`（`ApiResult<FileVO>`）；`NoteAttachmentService` POST 返回 `NoteAttachmentVO`；新增错误码 `TASK_ATTACHMENT_NOT_LINKED(50020)`、`TASK_ATTACHMENT_FORBIDDEN(50021)`；`unbindTaskAttachments` `@Transactional` + 删除审计 |
| 40 | note 附件关联幂等 | `POST /notes/{id}/attachments` 已存在关联返回现有 `NoteAttachmentVO`（不 409）；回滚删除失败记录日志并提示 |
| 41 | 附件下载鉴权 | 一律 axios `responseType:'blob'` 携带 Bearer；`downloadUrl` 仅作路径引用，不做 `<a href>`/`window.open` 直连（否则 `/api/v1/**` 全鉴权下返回 401 JSON） |
| 42 | 附件模块边界 | 公共接口 `AttachmentReferenceGuard`（referencedByNote/Task，仿 TagUsageCleaner）承担引用保护，file 模块不再反向依赖 note/task |
| 43 | 附件读权限拆分 | note 下载=`isVisible(note)&&关联存在`；task 下载=`isVisible(task)&&归属`；`canReadGeneric` 仅服务通用 `/files`（创建者/ADMIN） |
| 44 | task 附件删除按钮 | 后端返回 `canRemoveFromTask`（`assertWritable && (createdBy==me \|\| isAdmin)`），按钮 `v-if="canRemoveFromTask && !disabled"`，不用单独 `canDelete` |
| 45 | ADMIN 归档列表 | `scope=all` 支持 `archived=true` 查全部/他人归档（ADMIN 专用），补齐 §7 中 ADMIN 可读已归档的列表入口 |
| 46 | task 可见性 404 | `TaskAuthService` 新增 `isVisible(Long taskId, Long userId)` 重载；任务不可见→404，不直接复用抛 403 的 `assertVisible` |
| 47 | 附件/引用写前校验 | `POST /notes/{id}/attachments`、`POST /notes/{id}/relations` 必须先 `NoteAuthService.isWritable`（归档时 EDITOR 不可写），再看角色 |
| 48 | 部门集规模与引用幂等 | **部门树下推数据库（SQLite 递归 CTE `dept_id IN (SELECT id FROM dept_tree)`，保证全局排序/分页/总数/日历一致）；降级用单条 OR 分组或临时表 JOIN，禁止"多次查后内存并集"**；`note_relation` 重复添加幂等返回现有记录（不 409），与附件关联幂等口径一致 |
| 49 | note→TASK 引用选择器 | task 无 `visible` scope、`scope=all` 不表示 isVisible，故**新增轻量端点 `GET /tasks/search?keyword=&page=&size=`**（内部按 `isVisible(taskId,me)` 过滤）；前端 ≥2 字触发、300ms 防抖、最多 20 条、可滚动加载、已引用禁选、NOTE/TASK 双 Tab、目标已失效显示 404 兜底 |
| 50 | 前端列表架构 | 对齐任务模块"统一视图+共享列表+Pinia"：**`NoteListView`（壳）→ `NoteList`（容器）→ `NoteCard`（卡片）→ `useNoteList`（查询/分页/竞态）→ `stores/note`（跨页状态）**；7 个 scope 页只留"scope 配置+标题+权限分支"，杜绝 7 份复制；**动作（open/edit/pin/archive/delete/刷新/toast）统一抽 `useNoteActions()`**；补页面需求表（§10.5.1） |
| 51 | 列表权限字段 | `NoteBrief` 增 **`canManage`**；列表/详情同口径——编辑=`canEdit`（含归档规则，**不叠加 `!archived`**）、置顶/归档/删除/标签=`canManage`；前端禁 `canEdit` 控管理按钮、禁 `ownerId===me` 推导；补六身份卡片按钮矩阵 |
| 52 | 授权 VO 契约 | **`NoteUserGrantVO`** 全字段（id/双人/role/status/mode/direction/createdBy/审批/撤销/can*），`direction` 与 `can*` 由后端计算；ADMIN 全量视图只读+可代撤销 |
| 53 | blob 解析与续签 | **只保留一个解析点，收敛到 `http.ts` 成功拦截器**（blob+content-type json → `blob.text()` 解析，10002/20005 复用 `doRefresh()`+`_retried` 重放，其余抛 `BizError`）；`fetchAttachment` 只取路径/断言 Blob/文件名，**不二次解析**（消解 helper 与拦截器双重消费/职责冲突） |
| 54 | 日历契约 | `useMonthCalendar(loader,{initialScope})` 返回 `scope/setScope`；`BbMiniCalendar.loader(year,month,scope?)` + `watch(scope,load)` + 高度走 `--bb-calendar-height` CSS 变量 + 竞态取消；**点日跳转保留当前 scope**（`/notes/{scope}?date=`，非固定 `/notes`）；`/notes/calendar?scope=&year=&month=&date=` URL 契约；NoteCalendarView scope 选择器按角色显隐 |
| 55 | 导航与角色 | 导航项**补"探索"**；**AUDITOR 视为普通 note 用户（方案 A）**——导航显示 note 分组、可读自己/PUBLIC/被授权、可创建，不隐藏不灰态（见 §7/isVisible）；note 分组折叠状态持久化；`UserVO` 新增 `managerCanReadSubordinate` 下发（隐藏"下属"入口），并保留 `scope=subordinate` 开关关→403 兜底 |
| 56 | task 搜索响应 | `GET /tasks/search` 返回 **`PageResult 风格 {list,total,page,size}`**（非裸数组）；`status` 复用任务枚举；keyword 匹配 title、大小写不敏感、`<2` 字符返回空 list（不 400）；已删除不返回；已完成任务可返回并标记 |
| 57 | 上传签名统一 | **删 `uploadFile(file, taskId?)` 的 `taskId` 可选参**（通用 `/files` 已拒 taskId→400），新增 `uploadTaskFile(taskId, file)`；全仓库清旧调用点 |
| 58 | ownerTags 口径 | **`NoteVO`/`NoteBrief` 均不含 `ownerTags`，仅 `NoteDetailVO` 含**（且仅 `canEdit \|\| canManage` 返回），消解三处不一致 |
| 59 | 前端先行开发约定 | OpenAPI 先冻结 note schema + `UserVO.managerCanReadSubordinate`；MSW handlers/fixtures 为前端交付物（blob 3 用例 + 八角色数据）；冻结前允许临时手写过渡类型但交付以 `gen:types` 为准 + CI 校验；任务页 BbMiniCalendar 回归纳入现有 e2e |
| 60 | 行内编辑数据源 | **行内编辑先 `GET /notes/{id}` 取 `NoteDetailVO`（全文+ownerTags+version）再编辑**，绝不把 `NoteBrief` 摘要当全文（否则保存截短全文不可恢复）；`NoteEditor` 编辑态只收 `NoteDetailVO` |
| 61 | backlinks VO | 新增 **`NoteBacklinkVO`**（sourceNoteId/sourceSummary/sourceOwner*/relationId/createdBy/createdByName 引用人）；`NoteDetailVO.backlinks: NoteBacklinkVO[]`，relations 用 `NoteRelationVO`（target 语义），两语义不混用 |
| 62 | 编辑权限规则 | **编辑内容 = `canEdit`**（`canEdit` 已含归档 EDITOR=false/owner-admin=true），**删除 `&& !archived`**（避免挡掉归档 owner/admin）；管理操作 = `canManage` |
| 63 | 上传签名/限制 | **统一 `uploadFile(file, opts?)` / `uploadTaskFile(taskId, file, opts?)`，`opts={onProgress,signal}`**（进度/取消入口）；`maxSize/allowedExt` 从后端 `/files/config` 下发，前端不硬编码 100MB/四类 |
| 64 | 授权 Tab 查询 | `GET /note-grants` 用意图参数 `tab=created\|granted\|incoming\|all`（单端点覆盖四 Tab）；**"我发起的"=`tab=created`（`created_by=me`）**、**"我授权的"=`tab=granted`（`grantor_id=me` 全状态，含"待我确认"职责，确认/拒绝在 PENDING 行内展示）**、**"涉及我的"=`tab=incoming`（`grantee_id=me AND created_by<>me`）**、ADMIN `tab=all`；SUPERIOR 行（grantor=A/grantee=S/created_by=S/PENDING）S 在 created 可撤回、A 在 granted 可确认/拒绝且在 APPROVED 后可撤销/升级、S 的 incoming 不重复 |
| 65 | AUDITOR 角色 | **方案 A（普通 note 用户）**：不因 AUDITOR 拒绝 note 读/写，可读自己/PUBLIC/被授权（不存在"默认不可读"与导航"隐藏或灰态"两种口径） |
| 66 | 标签数据源 | note 侧显式 **`metaStore.loadTags()`**（NoteHomeView/NoteListView 初始化，或 DefaultLayout note 路由分支）；详情编辑他人优先 `ownerTags`；`ownerTags===undefined` 才禁用保存，**空数组是合法值**（可保存无标签） |
| 67 | scope 单一来源 | **列表页 scope 以 `route.meta.scope` 为唯一来源，URL 不写 `scope`（日历页例外）**；`stores/note` 缓存 key 用 `scope+query`；归档分段用两路由 `/notes/archived` 与 `/notes/archived/all` |
| 68 | NoteEditor 字段初始化 | **编辑态 `note` 初始化读取全部可编辑字段** `content/logDate/visibility/tagIds/version/ownerTags`（5 个渲染/保存字段不落到组件默认值），`modelValue` 仍只受控 content；**创建态默认值由前端先行**：`logDate=今天(Asia/Shanghai)`、`visibility=PRIVATE`、`tagIds=[]`，不得默认 PUBLIC |
| 69 | 授权 Tab 语义 | **弃用 `tab=mine`（grantor_id OR created_by 并集）**——该并集使 SUPERIOR 行下属 A 误入"我发起的"、上级 S 在 created/incoming 双重复、且 S 无法撤销自己发起的 PENDING。改为**拆"授权关系"与"发起动作"两维**：`tab=created`（created_by=me）/ `tab=granted`（grantor_id=me 全状态，含待我确认职责）/ `tab=incoming`（grantee_id=me AND created_by<>me）/ `tab=all`（ADMIN）；**仅 `incoming` 用 `created_by<>me` 排除 S 发起行，`created` 绝不加**；三种 mode 均显式写 `created_by=发起人`；`revoke PENDING=grantor OR created_by OR ADMIN`；`direction` 仅行内展示（含 `none`） |
| 70 | 保存后刷新 | **保存成功后清空筛选、按当前排序重拉第一页 + toast"已保存"**（方案 A）；不跳 logDate 所在页、不按位置本地插入；新卡片可能不在当前页，用户经筛选取回 |
| 71 | "全部"不新建 | **新建入口仅 `mine`（NoteHomeView）提供**；`/notes/all`（ADMIN）为管理视图不提供新建（避归属/可见性混淆） |
| 72 | composer 归属（方案 A） | **页面 owns 交互**：`NoteListView` 只做布局壳，提供 `#composer`/`#above-list`/`#editor` 具名插槽、读 `route.meta`；**壳内渲染 `NoteFilterBar`（`#filters` 插槽已移除，筛选条由壳内部用 `filterConfig`+`query`+`@change` 渲染）**；`NoteHomeView` 经插槽放入 `NoteEditor` 并持有 `POST`/保存/临时卡片附件区状态；`NoteList` 事件由页面外层处理并经公共 **`useNoteActions()`** 承载 |
| 73 | note_relation 字段 | `note_relation` 增 **`created_at`（引用时间）+`created_by`（引用人，可空）**——阶段一 V6 建表即定，避免返工；`note_attachment_ref` 不加列，`NoteAttachmentVO.createdAt/createdBy=上传时间/上传者（取自 attachment）`；**`NoteRelationVO`/`NoteBacklinkVO` 增 `createdName/createdByName`（引用人姓名，可空显示"—"）** |
| 74 | 新建后状态（方案 A） | **保存后保留顶部"刚创建"临时卡片**（noteId+附件区+完成/放弃按钮，独立于列表，经 `#above-list` 插槽渲染；`pendingCreatedNoteId` 跨路由恢复），同时清空筛选+按当前排序重拉第一页+toast；不跳 logDate 页、不本地插入；下一条先完成/放弃第一条防串条 |
| 75 | 授权单端点 | `GET /note-grants?tab=created\|granted\|incoming\|all` 一个端点覆盖全部 Tab，**删除独立 `/pending`、`/all`**；`direction` 仅行内展示（含 `none`），不参与 Tab 过滤 |

## 13. 后续可扩展

| 功能                     | 触发条件                  |
| ---------------------- | --------------------- |
| 评论 / emoji / 每日回顾 / 导出 | 用户需要                  |
| 轻量 markdown            | 用户需要                  |
| MCP / AI               | AI 辅助写作               |
| FTS5 全文搜索              | note 超 1 万            |
| task 详情展示关联 note       | 引用使用率高                |
| 部门级授权                  | 按部门批量授权               |
| 站内通知 / 收件箱             | 通知变高频                 |
| grant 有效期 expires_at   | 临时授权                  |
| 附件显式转交/共享              | 需要把别人附件转挂到自己日志        |
| task owner 删除他人上传附件    | 产品需要（本期仅上传者/ADMIN 可删） |
| 回收站                    | 需要恢复                  |
