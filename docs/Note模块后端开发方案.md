# Note 模块后端开发方案

> 本文档为 note 模块**后端**实现契约：数据模型、API、权限、错误码、实施与验收。
> 前端实现见《Note 模块前端开发方案》；总览与取舍背景见《Note 模块集成方案》。

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
   - `NoteFilterBar` 增加 `tagOptions?: Array<{ id?: number; name: string; count?: number }>` prop，由页面按 scope 调用此端点填充；mine 用 id、其他 scope 用 name。OpenAPI 同步 schema（前端文档 §6）、mock 与验收矩阵（§11 A-L）。
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
│   ├── NoteVO.java                    # + version + tags(本 note 已选) + canEdit/canManage/grantRole（**不含 ownerTags**，见 前端文档 §6）
│   ├── NoteDetailVO.java              # NoteVO + relations/backlinks/attachments + ownerTags（唯一含 ownerTags 处）
│   ├── NoteBacklinkVO.java            # backlinks 来源（sourceNoteId/sourceSummary/sourceOwner*/createdBy/createdByName，见 前端文档 §6）
│   ├── NoteBrief.java                 # + version
│   ├── NoteRelationVO.java            # relations 目标（id/targetType/targetId/targetSummary/createdAt/createdBy/createdByName，与 前端文档 §6 完全一致）
│   ├── NoteAttachmentVO.java          # note 附件（id=关联id/attachmentId/downloadUrl/canRemoveRef/canDelete/createdBy，见 前端文档 §6）
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

| 方法          | 路径                                        | 说明                           | 权限                                          |
| ----------- | ----------------------------------------- | ---------------------------- | ------------------------------------------- |
| POST        | `/api/v1/notes`                           | 创建                           | 登录                                          |
| PUT         | `/api/v1/notes/{id}`                      | 更新（携带 version）               | owner / EDITOR grant / ADMIN                |
| DELETE      | `/api/v1/notes/{id}`                      | 软删除 + 级联                     | owner / ADMIN                               |
| GET         | `/api/v1/notes`                           | 列表（scope + 过滤）               | 登录                                          |
| GET         | `/api/v1/notes/{id}`                      | 详情                           | 按可见性（不可见→404）                               |
| POST/DELETE | `/api/v1/notes/{id}/pin`                  | 置顶/取消                        | owner / ADMIN                               |
| POST/DELETE | `/api/v1/notes/{id}/archive` `/unarchive` | 归档/取消                        | owner / ADMIN                               |
| GET         | `/api/v1/notes/tags?scope=...`            | 标签 facet | 登录（scope=all 仅 ADMIN；scope=subordinate 受开关） |

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

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/v1/users?scope=subordinate` | 返回当前用户的下属用户列表（`PageResult<UserVO>`），供 `BbUserPicker scope='subordinate'` 与 `NoteGrantForm mode=SUPERIOR` 使用 | 登录 |

- **响应 schema**：`ApiResult<PageResult<UserVO>>`（`{list: UserVO[], total, page, size}`），仅返回当前用户直接下属（非递归）。
- **权限**：任何登录用户可调；`managerCanReadSubordinate=false`（开关关闭）时返回**空列表 `[]`**（非 403——该端点是用户选择器数据源，空列表表示"无下属可选"，403 会破坏表单初始化；scope=subordinate 的 403 只针对 note 列表/日历查询）。
- **OpenAPI**：纳入 `openapi.yaml` 与 mock 36 端点清单（非新增端点计数——复用现有 `GET /users` 加 `scope` query 参数，不新增 path）。

### 5.2 用户级授权

| 方法     | 路径                                 | 说明                                                                                                                                                                    | 权限                                    |
| ------ | ---------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| POST   | `/api/v1/note-grants`              | 发起授权（body 含 mode；三 mode 均显式写 `created_by=发起人`）                                                                                                                        | 登录                                    |
| GET    | `/api/v1/note-grants`              | 列表（`tab=created\|granted\|incoming\|all` 意图参数 + status/role/mode 过滤，`direction` 仅行内展示；转义 `\|` + 统一 4 列） | 登录（`tab=all` 仅 ADMIN）                 |
| POST   | `/api/v1/note-grants/{id}/approve` | PENDING→APPROVED                                                                                                                                                      | **grantor==me**（SUPERIOR 行的下属 A）      |
| POST   | `/api/v1/note-grants/{id}/reject`  | PENDING→REJECTED                                                                                                                                                      | **grantor==me**                       |
| POST   | `/api/v1/note-grants/{id}/upgrade` | VIEWER→EDITOR                                                                                                                                                         | **grantor==me**（仅 APPROVED）           |
| DELETE | `/api/v1/note-grants/{id}`         | 撤销（→REVOKED）：**撤 PENDING = `grantor==me OR created_by==me` 或 ADMIN；撤 APPROVED = `grantor==me` 或 ADMIN**（发起人可撤回自己的 PENDING）                                  | grantor / created_by(PENDING) / ADMIN |

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

| tab                            | 唯一过滤条件                                 | 按钮语义                                                                                                                                                                                                                    |
| ------------------------------ | -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `created`（我发起的）                | `created_by = me`                      | 发起人：OWNER/SUPERIOR/ADMIN 代授权均在此；可撤回自己发起的 PENDING；**APPROVED 行中：`grantor==me`（OWNER 行）可升级/撤销；`isAdmin && created_by==me`（ADMIN 代授权行，grantor=A）可撤销、不可升级；其余只读**                                             |
| `granted`（我授权的， 方案 A） | `grantor_id = me`（**全状态**）             | 我作为授权方可见全部状态（PENDING/APPROVED/REJECTED/REVOKED）；PENDING 行内显"**确认/拒绝/撤回（grantor==me，撤回后进入 REVOKED）**"；APPROVED 可撤销/升级——保证 SUPERIOR 行通过后 grantor A 仍有入口收回 |
| `incoming`（涉及我的）               | `grantee_id = me AND created_by <> me` | 只读/被授权；**加 `created_by<>me` 排除 S 自己发起的 SUPERIOR 行**（不重复）                                                                                                                                                                |
| `all`                          | ADMIN 全量                               | 只读 + 可代撤销                                                                                                                                                                                                               |

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
> **`GET /files/config` 契约**：响应 `{ code:0, data: { maxSize: number, allowedExt: string[] } }`——`maxSize` 单位为**字节**（如 52428800=50MB），`allowedExt` 空数组=不限制（非空=白名单，如 `['png','jpg','pdf']`，统一无点号 + 大小写归一化）；登录即可访问（不限制角色）；纳入 OpenAPI schema 与 mock 36 端点清单（见 前端文档 §6/前端文档 §7.11）；`useFileConfig()` composable 加载此端点，失败时用 fallback 常量（50MB/空数组）。

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
> 7. **`DELETE /files/{id}` 与 `FileVO.canDelete`**：`FileVO.canDelete` **仅表示"当前用户可物理删除该文件"**（`createdBy==me || isAdmin`，**不含引用保护**）；`BbAttachmentList` 的 task 删除按钮**改读 `v-if="file.canRemoveFromTask && !disabled"`**（`canRemoveFromTask` = `assertWritable && (createdBy==me||isAdmin)`，由后端计算），不再单独用 `file.canDelete`（避免 task 附件天然带 task_id 误判）。task 附件删除走第 5 条 task 专用端点；note 关联删除另由 note 级权限控制（见 前端文档 §4 权限表）。
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

   **前端**：`BbAttachmentList` 上传改走 `uploadTaskFile(taskId, file, opts?)` → task 专用上传端点；note 附件仍走 `uploadFile(file, opts?)`（taskId=null）→ `POST /notes/{id}/attachments`（签名以 前端文档 §7.7 为准）。

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
8. **AUDITOR 视为普通 note 用户（方案 A）**：`isVisible`/创建/列表/详情/授权等接口**不因 AUDITOR 角色额外拒绝**——可读自己的、PUBLIC、被授权的日志，可创建/编辑自己的日志；导航显示 note 分组（见 前端文档 §5.2），不隐藏、不灰态。AUDITOR 的"审计"职责限定在审计模块自身，与 note 访问权限解耦。
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

## 10. 实施计划

### 10.1 阶段一：后端核心

- [ ] V6\_\_note.sql（5 表 + 1 视图 + tag(name) 索引）
- [ ] domain + infrastructure Mapper（Note @Version/@TableLogic）
- [ ] NoteCommandService（CRUD + 字段级权限 + log_date + 级联删除 + PATCH + dept_id 刷新）
- [ ] NoteQueryService（7 scope + NoteVisibilitySqlBuilder + 批量装配 + version）
- [ ] NoteAuthService（isVisible + visibleDeptIds + 下属 + 归档 + deleted）
- [ ] NoteUserGrantService（mode + createdBy 视图 + 自动通过记录 approved_by）
- [ ] ErrorCode / LogicDeleteGuard
- [ ] NoteController / NoteUserGrantController

### 10.2 阶段二：后端完善（前端并行启动）

- [ ] CurrentUserDeptService + DepartmentPolicy.visibleDeptIds（空部门语义）
- [ ] NoteVisibilitySqlBuilder（scope=visible 同源判定）+ **§6.3 的 15 行 Java/SQL 等值测试矩阵**
- [ ] AttachmentAccessService（**实体无关**的 canBind/canReadGeneric/canDelete）+ `AttachmentReferenceGuard`（Note/TaskReferenceGuard 实现）+ FileService（增量绑定/经 guard 双引用保护/FileController 收紧/FieldCleanupJob 改造）
- [ ] **TaskAuthService 新增 `isVisible(Long,Long)` 重载（不可见→404）；TaskAttachmentController（task 专用上传/下载/预览/删除端点 + canRemoveFromTask）+ unbindTaskAttachments 契约（@Transactional + 错误码 50020）+ 返回类型统一（upload→FileVO）+ TaskQueryService/TaskVO.downloadUrl + BbAttachmentList 改造（下载走 axios blob、删除按钮 `canRemoveFromTask && !disabled`）**
- [ ] NoteAttachmentService（关联附件 `isWritable` + assertCanBind + 引用计数 + note 下载 + canUpload/canRemoveRef + **返回 NoteAttachmentVO（含 note 专用 downloadUrl）+ 关联幂等**）
- [ ] NoteRelationService（`isWritable`、添加/backlinks 幂等、自引用、TASK 可见→404、scope=visible 搜索、backlinks 404；**backlinks 返回 NoteBacklinkVO（source 字段 + createdBy/createdByName 引用人）；`note_relation` 写 `created_at/created_by`，EDITOR 添加引用时 `created_by`=EDITOR；NoteRelationVO 增 createdByName**）
- [ ] TagService + TaskTagCleaner + NoteTagCleaner（`cleanByTagId` 事务）
- [ ] SpaRoutes（`/notes`、`/notes/**`）/ 日历接口（7 scope 与列表共用，all 非 admin 403；`scope=all&archived=true` ADMIN 归档；**`internal/visible` 部门树用递归 CTE `dept_id IN (SELECT id FROM dept_tree)`，不做内存并集）
- [ ] openapi.yaml（FileVO downloadUrl/createdBy/canDelete/canRemoveFromTask + NoteAttachmentVO/NoteRelationVO/**NoteBacklinkVO**/NoteDetailVO/NoteBrief 含 canManage + NoteUserGrantVO + `GET /tasks/search` 响应 `{list,total,page,size}` + `UserVO.managerCanReadSubordinate` + **`GET /note-grants` `tab=created|granted|incoming|all`（改用 granted，含 `NoteUserGrantVO.direction:'outgoing'|'incoming'|'none'`） + `GET /files/config`（`maxSize:number` 字节、`allowedExt=[]`=不限制） + `note_relation.created_at/created_by`+ `NoteRelationVO/NoteBacklinkVO.createdByName` + 创建/更新返回 NoteDetailVO** + 50020/50021 错误码 + `isVisible(Long,Long)` 重载；前后端类型同步）

> 前端启动项（列表壳/容器、composable、store、`http.ts`、mock 等）见前端文档 §8.1。

### 10.3 阶段三：联调（后端侧）

- [ ] 迁移冒烟 V1→V6 + 视图查询
- [ ] 后端单元测试（权限矩阵、canBind、乐观锁、删除级联、授权状态机、dept_id 刷新、**visible 等值矩阵 15 行、task 附件归属/可见性、SUPERIOR 审计与开关、空部门、FieldCleanupJob 逻辑删除、`AttachmentReferenceGuard` 引用保护、`canRemoveFromTask`、`isVisible(Long,Long)` 404、**`dept_tree` 递归 CTE 与 >500 部门分页/排序/总数/日历一致性、附件下载 blob 鉴权**）
- [ ] 前后端联调

## 11. 接口验收矩阵

**格式**：`方法 + 路径 + 角色/前置 fixture + 请求 + 期望 HTTP 状态 + 期望业务码 + 期望响应关键字段 + 测试类型（单测/e2e）`。每行分配固定 fixture（用户 u_owner/u_editor/u_viewer/u_admin/u_dept/u_sub/u_public/u_auditor + 部门/授权/note/attachment/relation）。

**HTTP 状态 / 业务码分列说明**：后端业务异常与认证失败均 `ApiResult.fail(...)` 且 HttpStatus 恒为 `200`（由全局异常处理器统一映射），但 **HTTP 协议级错误**（401 未认证 / 403 权限拒绝 / 400 参数格式 / 404 路由不存在 / 405 方法不允许）走标准 HTTP 状态码、**不进入业务码**。故矩阵将"期望 HTTP 状态"与"期望业务码"分列：

- **期望 HTTP 状态**：`200`（业务成功或业务失败包在 `ApiResult.fail`）/ `400`（参数格式/路由参数）/ `401`（未登录，跳登录）/ `403`（权限拒绝，非业务码）/ `404`（路由不存在或 404 语义化）/ `409`（冲突）。
- **期望业务码**：仅 HTTP=200 时有意义，`0`=业务成功，`50001/50006/50007/50008/50013/50014/50017/50020/10002/10006` 为业务失败断言目标。

断言目标错误码汇总：50001（NOTE_NOT_FOUND）/ 50002（NOTE_CONTENT_TOO_LONG）/ 50003（NOTE_TAG_INVALID）/ 50006（NOTE_RELATION_TARGET_NOT_FOUND）/ 50007（NOTE_GRANT_DUPLICATE）/ 50008（NOTE_GRANT_SELF）/ 50012（NOTE_LOG_DATE_INVALID，未来超 7 天）/ 50013（NOTE_FIELD_FORBIDDEN）/ 50014（NOTE_GRANT_FLOW_FORBIDDEN）/ 50017（NOTE_DEPT_REQUIRED）/ 50020（TASK_ATTACHMENT_NOT_LINKED）/ 10002（未登录）/ 10006（VERSION_CONFLICT）。1c=50002、1d-2=50012、1e=50003 已补精确码；50018 不再出现（同一附件允许多 note 引用）。**仅作内部语义、不在矩阵断言**：50015（NOTE_GRANT_MODE_INVALID，HTTP 400）/ 50016（NOTE_SCOPE_DISABLED，HTTP 403；前端捕获 `err.status===403` 而非 code，见 前端文档 §4/前端文档 §7.9）/ 50018（NOTE_ATTACHMENT_CANNOT_BIND，HTTP 403，权限层）/ 50019（NOTE_ATTACHMENT_BOUND，task 场景）/ 50021（TASK_ATTACHMENT_FORBIDDEN，HTTP 400）/ 50004（NOTE_ATTACHMENT_INVALID，内部校验）/ 50005（NOTE_ATTACHMENT_NOT_LINKED，内部校验）/ 50009（NOTE_GRANT_NOT_FOUND，404 语义）/ 50010（NOTE_GRANT_NOT_PENDING，归入 50014 流程）/ 50011（NOTE_GRANT_NOT_SUBORDINATE，HTTP 403）。

##### A. Note CRUD

| #    | 方法     | 路径            | 角色/前置                                                 | 请求                                                        | 期望 HTTP | 期望业务码 | 期望响应关键字段                                                                                                                                                                                                                                                                                                                                                        | 测试类型 |
| ---- | ------ | ------------- | ----------------------------------------------------- | --------------------------------------------------------- | ------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1    | POST   | `/notes`      | owner                                                 | `{content,logDate,visibility:PRIVATE,tagIds:[]}`          | 200     | 0     | `NoteDetailVO{id,version,contentSummary,ownerTags,attachments,relations}`                                                                                                                                                                                                                                                                                       | e2e  |
| 1a   | POST   | `/notes`      | 未登录                                                   | —                                                         | 401     | —     | 跳登录（不返回 `ApiResult`）                                                                                                                                                                                                                                                                                                                                            | e2e  |
| 1b   | POST   | `/notes`      | 登录（任何登录用户含 EDITOR 均可创建自己的 note，§5.1 "创建权限=登录"） | `{content:"...",logDate:今天,visibility:PRIVATE,tagIds:[]}` | 200     | 0     | `NoteDetailVO`                                                                                                                                                                                                  | e2e  |
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
| 7a   | DELETE | `/notes/{id}` | owner                                                 | —                                                         | 200     | 0     | 软删除+级联（`note_tag`/`note_attachment_ref`/`note_relation`（source+target 两侧）清理 + `UPDATE note SET deleted=1`，§3.4；**不清理 `note_user_grant`**——属用户级全量授权，删单条 note 撤销会导致该 grantor 对所有其他日志的授权一并失效，属严重授权数据破坏）；**附加断言**：删除后 `note_user_grant` 中该 grantor 的 APPROVED 行**仍存在**，grantee 仍可访问该 grantor 的其他 note（验证不误级联） | e2e  |
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
| 24a | GET    | `/note-grants?tab=created`  | 登录                                | —     | 200     | 0     | `created_by=me` 全状态列表（**含 SUPERIOR 行 S 发起的 `created_by=S`、ADMIN 代授权行 `created_by=ADMIN`**，§5.2；**非 `grantor_id=me`**，否则漏 SUPERIOR/ADMIN 代授权）                                    | e2e  |
| 24b | GET    | `/note-grants?tab=granted`  | 登录                                | —     | 200     | 0     | `grantor_id=me` 全状态列表（**我作为授权方**，§5.2；**与 `tab=created`（`created_by=me`）是不同维度**：created 含 SUPERIOR/ADMIN 代授权行，granted 不含）                    | e2e  |
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
| 50 | 前端列表架构 | 对齐任务模块"统一视图+共享列表+Pinia"：**`NoteListView`（壳）→ `NoteList`（容器）→ `NoteCard`（卡片）→ `useNoteList`（查询/分页/竞态）→ `stores/note`（跨页状态）**；7 个 scope 页只留"scope 配置+标题+权限分支"，杜绝 7 份复制；**动作（open/edit/pin/archive/delete/刷新/toast）统一抽 `useNoteActions()`**；补页面需求表（前端文档 §7.1） |
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

> 本表是前后端共用契约（75 项取舍），与《Note 模块前端开发方案》§10 保持一致；后端实现涉及的行在前文各节展开。

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
