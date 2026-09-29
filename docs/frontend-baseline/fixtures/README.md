# Fixtures（固定 Mock 数据）规范

> 依据 `../../../Task/03前端模块详细设计.md §2.3.2（修订 M3）`：旧前端用 Playwright `page.route` 拦截旧接口、新前端用 **MSW 加载同一份 fixtures**——JSON 序列化需**跨新旧前端共享、版本化**，保证同数据对比。本目录即该共享 fixtures 的规范与占位。

## 1. 定位

- 目的：让旧/新前端在**同一份数据**下渲染，比对行为与视觉，避免因数据差异产生误报。
- 归属：仓根 `docs/frontend-baseline/fixtures/`（不置于 `frontend/` 下）。
- 与 API 契约关系：字段以 `../../api/openapi.yaml`（单一事实源）为准；本目录为按该契约的**样例数据**。

## 2. 目录 / 文件命名

```
fixtures/
├── README.md                       # 本文
├── api/                            # 【新接口形态】MSW 用，按接口路径镜像
│   ├── auth/POST.login.json · POST.logout.json · POST.refresh.json · GET.external-config.json
│   ├── tasks/                      # 读态
│   │   ├── GET.list.json           # 六大视图列表（含周期任务；E-03/E-16）
│   │   ├── GET.detail.json         # 含子任务
│   │   ├── GET.subtasks.json       # 子任务列表（GET /tasks/subtasks?parentId=）
│   │   ├── GET.count.json          # 各视图计数
│   │   └── GET.calendar.json       # 日历区间（周期实例展开；E-16）
│   │                               # 写态（2026-09-29 补）
│   │   ├── POST.create.json · PUT.update.json · DELETE.remove.json
│   │   └── POST.complete.json · POST.uncomplete.json · POST.collect.json · DELETE.collect.json
│   ├── users/GET.index.json · GET.me.json · POST.create.json · PUT.password.json
│   ├── departments/GET.tree.json   # 部门树（GET /departments）
│   ├── tags/GET.list.json · POST.create.json · PUT.update.json · DELETE.remove.json
│   ├── categories/
│   │   ├── GET.tree.json           # 分类树（全字段 + 3 级嵌套）
│   │   ├── GET.detail.json         # 单节点「详情态」对象
│   │   ├── GET.tree.empty.json     # 空态：无分类
│   │   └── POST.create.json · PUT.update.json · DELETE.remove.json
│   ├── menus/
│   │   ├── GET.list.json           # 自定义栏（全字段 + 条目含任务摘要）
│   │   ├── GET.detail.json         # 单栏「详情态」对象（含 items）
│   │   ├── GET.list.empty.json     # 空态：无自定义栏
│   │   └── POST.create.json · PUT.update.json · DELETE.remove.json · POST.items.json · DELETE.items.json
│   ├── files/POST.upload.json      # 上传响应（FileVO）
│   ├── _errors/                    # 错误态（ApiResult，code!=0）+ README
│   └── audit/GET.logins.json · GET.operates.json
└── legacy-api/                     # 【旧接口形态】黄金截图用（Playwright route 注入）
    ├── README.md
    └── dataset.json                # 旧系统真实响应结构 + 合成数据（含 @task 引用）
```

## 1.1 双形态（修订 0301/M2 落地）

基线 fixtures 存在**两种序列化形态**，表达**同一逻辑数据集（字段/语义对齐；样例值与 id 命名空间各自合成，非逐字节同一份）**：

| 形态 | 目录 | 消费方 | 外层/字段 |
|---|---|---|---|
| **新接口形态** | `api/` | 新前端 MSW | `ApiResult{code,message,data}`，字段以 `openapi.yaml` 为准 |
| **旧接口形态** | `legacy-api/` | 旧前端 Playwright route（黄金截图） | 旧系统真实结构（`{records,...}` / 裸数组 / `taskContent` 等） |

> 原因：旧/新接口**报文结构本就不同**（旧 `task/record/*` vs 新 `/tasks`），无法用一份字节级 JSON 同时喂两端。故约定**同一逻辑数据集、两种形态**，字段映射见各 README。黄金截图证据由 `legacy-api/` 驱动（`../screenshots/README.md`）。

> **身份对齐（0304/G1，最高优先级）**：`legacy-api` 与 `api/` 的**当前用户均为 `admin`（`id=1`）**，任务 `owner`（新）/`belongUserId`（旧）同为 `1`，因此「我的一天 / 分配给我 / 我参与 / 我的收藏」在两端过滤结果一致（旧 id 为字符串、新为整数，类型见 §3）。`api/users/GET.me.json` 已对齐为 `id=1`（原 `100` 已废弃）。

> **现状态 vs 目标态（0304/G2、§2）**：`api/` 与 `../../api/openapi.yaml` 描述的是**目标态**（`/api/v1` + 成功码 `code==0`）；`legacy-api/` 是**现状态**（`/task/record/*` 裸 `Page`/裸实体，无 `code`）。二者**刻意不兼容**，不可用同一 handler 互喂；`code==0` 为 `02 §1.3` 设计决策，非缺陷。

## 1.2 详情态 / 空态 fixture 口径（2026-09-29）

对 **`categories` / `menus`** 两类资源，设计文档（`02 §3.x` 接口表）**无独立 `GET /{id}` 详情接口**（分类编辑复用树节点、自定义栏数据来自 `GET /menus`）。因此：

| 文件 | 含义 | 消费方用法 |
|---|---|---|
| `categories/GET.tree.json` | 分类树，**每节点为完整详情对象**（`sort/taskCount/createdAt/updatedAt/children`，含 3 级嵌套） | `GET /categories` 直接返回 |
| `categories/GET.detail.json` | **单个分类节点**的完整对象（取自树内节点形态） | MSW 对「单节点」场景（编辑表单/详情面板）按 id 从树 handler 中**取出该节点**返回；**不新增后端接口** |
| `categories/GET.tree.empty.json` | 空树 | 空态场景（配合 `BbEmpty`） |
| `menus/GET.list.json` | 自定义栏列表，**每栏含完整 `items[]`**，条目内嵌 `task` 摘要（`TaskBrief`） | `GET /menus?userId=` 直接返回 |
| `menus/GET.detail.json` | **单个自定义栏**完整对象（含 `items[]`） | 同 `categories/GET.detail.json` 口径，按 id 从列表 handler 取出；**不新增后端接口** |
| `menus/GET.list.empty.json` | 无自定义栏 | 空态场景 |

> **契约支撑**：本次同步在 `../../api/openapi.yaml` 补齐了此前缺失的 `Category` / `Menu` / `MenuItem` / `TaskBrief` 四个 schema（原 fixtures 无契约可依）。若后续确需「按 id 取单分类/单栏」的**独立接口**，需先改 `02 §3.x` 接口表与 openapi，再新增对应 `GET /{id}`——当前**刻意保守**，不擅自新增端点。

## 1.3 写操作 / 错误态 fixtures（修订 0302/P3、P5；0303/N5 修正计数）

`api/**` 此前仅覆盖**读态 + 登录**，本次补齐两类：

**写操作（P3，实测 21 份）** —— 与 `openapi.yaml` 一一对应；成功返回 `ApiResult{code:0}`，create 返回新 id，其余 `data:null`（`Ok` 的 `data` 为 nullable）：

| 端点 | fixture |
|---|---|
| `POST /tasks` | `tasks/POST.create.json`（`data:{id}`） |
| `PUT /tasks/{id}`、`DELETE /tasks/{id}` | `tasks/PUT.update.json`、`tasks/DELETE.remove.json` |
| `POST /tasks/{id}/complete`、`/uncomplete` | `tasks/POST.complete.json`、`tasks/POST.uncomplete.json` |
| `POST|DELETE /tasks/{id}/collect` | `tasks/POST.collect.json`、`tasks/DELETE.collect.json` |
| `POST /tags`、`/tags/{id}` PUT/DELETE | `tags/POST.create.json`、`PUT.update.json`、`DELETE.remove.json` |
| `POST /categories`、`/categories/{id}` PUT/DELETE | `categories/POST.create.json`、`PUT.update.json`、`DELETE.remove.json` |
| `POST /menus`、`/menus/{id}` PUT/DELETE、`/menus/{id}/items` POST、`/items/{itemId}` DELETE | `menus/POST.create.json`、`PUT.update.json`、`DELETE.remove.json`、`POST.items.json`、`DELETE.items.json` |
| `POST /files` | `files/POST.upload.json`（`FileVO`） |
| `POST /users`、`PUT /users/{id}/password` | `users/POST.create.json`、`PUT.password.json` |

> 参与人 / 附件绑定：走 `PUT /tasks/{id}`（`participantIds[]` / `fileIds[]`），无独立端点；上传走 `POST /files`。
> 同步对齐 `openapi.yaml`：补 `PUT/DELETE /menus/{id}`、`DELETE /menus/{id}/items/{itemId}` 三个此前遗漏的端点（与 `02 §3.x` 一致）。
> **已知覆盖缺口**：`PUT /users/{id}`（更新用户）与 `DELETE /users/{id}`（禁用/删除）虽在 openapi 中定义，但**本期未产出对应写态 fixture**（用户管理非前端基线样例重点）；如需覆盖，后续按 `02 §2.5` 补 `users/PUT.update.json` / `users/DELETE.remove.json`。

**错误态（P5；**0304/P2 扩至全覆盖**；**0306 增 3 码**）** —— `_errors/` 提供 `ApiResult{code!=0}` 错误体，**覆盖 `02 §1.4` 全部 22 个非 0 错误码**（10000/10001/10002/10003/10004/10005/10006/10007/20001/20002/20003/20004/20005/20006/30001/30002/30003/30004/40001/40002/40003/60001），供 MSW 错误分支使用；**HTTP 状态码不入 JSON**，由 handler 决定（业务错误=HTTP200+JSON；传输层错误=空 body/5xx），详见 `_errors/README.md`。

## 3. JSON 表达约定

- **统一外层**：非列表接口用 `ApiResult{code:0, message, data, traceId}`；列表用 `ApiResult{data:{list,total,page,size}}`（与 02 §1 一致）。
- **时间**：ISO8601 + `+08:00`（如 `"2026-10-01T18:00:00+08:00"`），不用时间戳（02 §1.8 jackson）。
- **周期任务**：`cycle_rule` TEXT(JSON) 全字段 `{freq,interval,dtstart,byDay,count,until,tz}`；`cycle_last_completed` 按推进/未开始给出（E2E E-16 依赖）。
- **脱敏**：人员 `mobile/phone` 一律 `138****0000` 形式（02 手机号脱敏）。

## 4. 数据字典（节选，全量以 openapi.yaml 为准）

| DTO | 关键字段 | 说明 |
|---|---|---|
| `TaskVO` | `id,title,content,status(ACTIVE/DISABLED/ARCHIVED),completed,dueAt,remindAt,priority(priority),cycleRule,cycleLastCompleted,owner:{id,name},assignees[],ccUsers[],participantIds[],category:{id,name},tags[],subtasks[],files[]` | `owner.id` 映射旧 `belongUserId`（02 §4.5） |
| `UserVO` | `id,username,name,mobile(脱敏),deptId,deptName,roleCode` | `GET /users?deptId=&keyword=` |
| `CycleRule` | `freq,interval,dtstart,byDay,count,until,tz` | 02 §4.3；`dueAt=dtstart` |
| `TagVO` | `id,name,color(owner)` | 标签着色 |
| `Category` | `id,name,parentId,scope(PERSONAL/DEPARTMENT/ORG),deptId,ownerId,sort,taskCount,createdAt,updatedAt,children[]` | 分类树节点（含子）；`GET /categories`（`scope` 共享范围，ADR-015） |
| `Menu` | `id,userId,name,sort,createdAt,items[]` | 自定义栏（用户私有）；`GET /menus?userId=` |
| `MenuItem` | `id,menuId,taskId,sort,task:TaskBrief` | 栏内条目，内嵌任务摘要供直接渲染 |
| `TaskBrief` | `id,title,completed,dueAt,priority` | 栏内/轻量引用任务的摘要 |
| `Department` | `id,name,parentId,leaderId,sort,children[]` | 部门树节点；`GET /departments`（`leaderId`=负责人，组织级可见性 ADR-012；guides：部门筛选/人员选择数据源；原 `BbOrgTree` 已于 0307 废弃） |
| `CountVO` | `{day,week,joined,assigned,collect,all}` | 六大视图计数（recurring 按展开实例） |

> **身份 / 命名空间（0304/G1）**：当前用户 = `admin`（新形态 `id=1`；旧形态 `id="1"` 字符串），任务归属人 = `1`。两形态**样例值不逐字节对应**（id 命名空间各自独立），仅字段与语义对齐；比对以「同视图、同过滤结果」为准。

## 5. MSW / Playwright 契约

- **新前端（MSW）—— 已直连（2026-09-29）**：`frontend/src/mocks/fixtures.ts` 以 `import.meta.glob('../../../docs/frontend-baseline/fixtures/api/**/*.json', { eager: true })` 注册全部 fixtures，键为 `<目录>/<METHOD>.<name>`（如 `tasks/GET.list`）；`frontend/src/mocks/handlers.ts` 直接消费：
  - **读态**原样返回 fixture 信封（`tasks/GET.list` 按 `scope/keyword` 过滤子集、`GET.count` 原样）；
  - **错误分支**复用 `_errors/<code>.<name>`（如 `20003.bad-credentials`、`10006.version-conflict`、`30001.task-not-found`）；
  - **写态**在内存副本上变更（重启重置），返回对应写态 fixture 信封。
  - 覆盖度由 `frontend/vitest` 的 `tests/unit/mocks-fixtures.spec.ts` 断言（键存在 + 身份对齐 + 错误码），fixtures 目录漂移即失败。
  - 放行：fixtures 在 `frontend/` 之外，`vite.config.ts` 需 `server.fs.allow` 指向仓根（已配置）。
- **旧前端（Playwright）**：`page.route('**/api-server/**', route => route.fulfill({ json: <对应 fixture> }))`，路径与 MSW handler 对齐。
- **版本化**：fixtures 变更需随契约（openapi.yaml）同步；回归时始终引用**同一 commit** 的 fixtures，避免新旧前端用不同版本数据比对。

## 6. 当前状态

- ✅ **MSW 直连 fixtures（2026-09-29）**：`frontend/src/mocks/{fixtures,handlers}.ts` 改为消费本目录（`api/**`），不再内联 mock 数据；新前端 E2E（`frontend/e2e/`）与单测（`tests/unit/mocks-fixtures.spec.ts`）同源，`pnpm -C frontend test:e2e` = 24 passed。详见 §5。
- ✅ **新接口形态**（`api/`，2026-09-28）：`tasks/GET.list|detail|count|calendar.json`、`users/GET.index|me.json`、`tags/`、`categories/`、`menus/`、`audit/`、`auth/POST.login.json`，字段对齐 `../../api/openapi.yaml`。
- ✅ **分类 / 自定义栏覆盖率补齐**（2026-09-29）：`categories` 增加**全字段 + 3 级嵌套**树、单节点详情态（`GET.detail.json`）、空态（`GET.tree.empty.json`）；`menus` 增加**条目内嵌任务摘要**的列表、单栏详情态（`GET.detail.json`）、空态（`GET.list.empty.json`）。同步补齐 openapi `Category/Menu/MenuItem/TaskBrief` schema（详见 §1.2）。
- ✅ **周期任务全字段样例**（E-16 依赖，新形态）：`GET.list.json` 覆盖 DAILY 无限、WEEKLY+`byDay`+`count`、MONTHLY+`until`、`cycleLastCompleted` 已推进/未开始各一；`GET.calendar.json` 演示展开实例。
- ✅ **写操作 / 错误态覆盖**（2026-09-29，修订 0302/P3、P5；计数 0303/N5 修正）：写态 **21 份**（tasks 7 + tags 3 + categories 3 + menus 5 + users 2 + files 1）+ 子任务读态；错误态 **9 份** + `_errors/README.md`（对齐 `02 §1.4`）；同步补 openapi `PUT/DELETE /menus/{id}` 等 3 端点。详见 §1.3。
- ✅ **四审（0304）修订**（2026-09-29）：① **G1 身份对齐**——`users/GET.me.json` 当前用户 `id=1`（admin），与 `legacy-api` 一致，`owner.id`/`belongUserId` 均 `1`；② **G3**——补 `auth/POST.logout.json`、`POST.refresh.json`、`GET.external-config.json`；③ **G4**——openapi 补 `Department`/`OperateLogVO`/`LoginLogVO`/`OperateLogPage`/`LoginLogPage` schema，并为 `/users/me`、`/departments`、`/tags`、`/categories`、`/menus`、`/audit/*` 定义 data schema；④ **P2 错误态**——`_errors/` 由 9 → **19**（`02 §1.4` 非 0 码全覆盖）；⑤ **G5**——删除孤儿 `users/GET.tree.json`（`BbOrgTree` 已改用 `departments/GET.tree.json`）；⑥ **G2**——显式标注 `api/` 为目标态、`legacy-api/` 为现状态（§1.1）。
- ✅ **企业级审核（0306）修订**（2026-09-29）：① **Q4 乐观锁**——`tasks/*` fixtures 增 `"version": 0`，openapi `TaskVO.version` / `TaskCreateReq.version`；② **Q1/Q2 角色与写权**——openapi `roleCode` 枚举扩为 `ADMIN/AUDITOR/USER_MANAGER/COMMON`；③ **错误态**——`_errors/` 19 → **22**（新增 `10006/10007/20006`）。
- ✅ **覆盖率缺口补齐**（2026-09-29，修订 0303/N2）：补 `users/GET.me.json`（`GET /users/me`）、`departments/GET.tree.json`（`GET /departments`）；`BbOrgTree` 数据源明确为后者（不再复用 users 树）。
- ✅ **旧接口形态**（`legacy-api/`，2026-09-28）：线上抓包结构 + 合成数据，驱动 `../screenshots/` 黄金截图；真实密钥/口令已脱敏（见 `legacy-api/README.md`）。
- ⏳ 待办：`users/GET.index.json` 按 `deptId/scope` 过滤子集（MSW handler 内过滤）；若确认需要「按 id 取单分类/单栏」独立接口，同步改 `02 §3.x` + openapi。
