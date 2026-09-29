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
│   ├── auth/POST.login.json
│   ├── tasks/                      # 读态
│   │   ├── GET.list.json           # 六大视图列表（含周期任务；E-03/E-16）
│   │   ├── GET.detail.json         # 含子任务
│   │   ├── GET.subtasks.json       # 子任务列表（GET /tasks/subtasks?parentId=）
│   │   ├── GET.count.json          # 各视图计数
│   │   └── GET.calendar.json       # 日历区间（周期实例展开；E-16）
│   │                               # 写态（2026-09-29 补）
│   │   ├── POST.create.json · PUT.update.json · DELETE.remove.json
│   │   └── POST.complete.json · POST.uncomplete.json · POST.collect.json · DELETE.collect.json
│   ├── users/GET.index.json · GET.tree.json · POST.create.json · PUT.password.json
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

基线 fixtures 存在**两种序列化形态**，表达**同一组逻辑数据**：

| 形态 | 目录 | 消费方 | 外层/字段 |
|---|---|---|---|
| **新接口形态** | `api/` | 新前端 MSW | `ApiResult{code,message,data}`，字段以 `openapi.yaml` 为准 |
| **旧接口形态** | `legacy-api/` | 旧前端 Playwright route（黄金截图） | 旧系统真实结构（`{records,...}` / 裸数组 / `taskContent` 等） |

> 原因：旧/新接口**报文结构本就不同**（旧 `task/record/*` vs 新 `/tasks`），无法用一份字节级 JSON 同时喂两端。故约定**同一逻辑数据集、两种形态**，字段映射见各 README。黄金截图证据由 `legacy-api/` 驱动（`../screenshots/README.md`）。

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

## 1.3 写操作 / 错误态 fixtures（修订 0302/P3、P5）

`api/**` 此前仅覆盖**读态 + 登录**，本次补齐两类：

**写操作（P3）** —— 与 `openapi.yaml` 一一对应；成功返回 `ApiResult{code:0}`，create 返回新 id，其余 `data:null`（`Ok` 的 `data` 为 nullable）：

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

**错误态（P5）** —— `_errors/` 提供 `ApiResult{code!=0}` 错误体（对齐 `02 §1.4 ErrorCode`：10001/10002/10003/10004/20003/20005/30001/30003/40002），供 MSW 错误分支使用；**HTTP 状态码不入 JSON**，由 handler 决定（业务错误=HTTP200+JSON；传输层错误=空 body/5xx），详见 `_errors/README.md`。

## 3. JSON 表达约定

- **统一外层**：非列表接口用 `ApiResult{code:0, message, data, traceId}`；列表用 `ApiResult{data:{list,total,page,size}}`（与 02 §1 一致）。
- **时间**：ISO8601 + `+08:00`（如 `"2026-10-01T18:00:00+08:00"`），不用时间戳（02 §1.8 jackson）。
- **周期任务**：`cycle_rule` JSONB 全字段 `{freq,interval,dtstart,byDay,count,until,tz}`；`cycle_last_completed` 按推进/未开始给出（E2E E-16 依赖）。
- **脱敏**：人员 `mobile/phone` 一律 `138****0000` 形式（02 手机号脱敏）。

## 4. 数据字典（节选，全量以 openapi.yaml 为准）

| DTO | 关键字段 | 说明 |
|---|---|---|
| `TaskVO` | `id,title,content,status(ACTIVE/DISABLED/ARCHIVED),completed,dueAt,remindAt,priority(priority),cycleRule,cycleLastCompleted,owner:{id,name},assignees[],ccUsers[],participantIds[],category:{id,name},tags[],subtasks[],files[]` | `owner.id` 映射旧 `belongUserId`（02 §4.5） |
| `UserVO` | `id,username,name,mobile(脱敏),deptId,deptName,roleCode` | `GET /users?deptId=&keyword=` |
| `CycleRule` | `freq,interval,dtstart,byDay,count,until,tz` | 02 §4.3；`dueAt=dtstart` |
| `TagVO` | `id,name,color(owner)` | 标签着色 |
| `Category` | `id,name,parentId,sort,taskCount,createdAt,updatedAt,children[]` | 分类树节点（含子）；`GET /categories` |
| `Menu` | `id,userId,name,sort,createdAt,items[]` | 自定义栏（用户私有）；`GET /menus?userId=` |
| `MenuItem` | `id,menuId,taskId,sort,task:TaskBrief` | 栏内条目，内嵌任务摘要供直接渲染 |
| `TaskBrief` | `id,title,completed,dueAt,priority` | 栏内/轻量引用任务的摘要 |
| `CountVO` | `{day,week,joined,assigned,collect,all}` | 六大视图计数（recurring 按展开实例） |

## 5. MSW / Playwright 契约

- **新前端（MSW）**：`src/mocks/handlers.ts` 的每个 `http.get('/api/v1/tasks', ...)` 返回 `fixtures/api/tasks/GET.list.json`（按 scope 过滤出对应子集以复用同一份数据）。
- **旧前端（Playwright）**：`page.route('**/api/**', route => route.fulfill({ json: <对应 fixture> }))`，路径与 MSW handler 对齐。
- **版本化**：fixtures 变更需随契约（openapi.yaml）同步；回归时始终引用**同一 commit** 的 fixtures，避免新旧前端用不同版本数据比对。

## 6. 当前状态

- ✅ **新接口形态**（`api/`，2026-09-28）：`tasks/GET.list|detail|count|calendar.json`、`users/GET.index|tree.json`、`tags/`、`categories/`、`menus/`、`audit/`、`auth/POST.login.json`，字段对齐 `../../api/openapi.yaml`。
- ✅ **分类 / 自定义栏覆盖率补齐**（2026-09-29）：`categories` 增加**全字段 + 3 级嵌套**树、单节点详情态（`GET.detail.json`）、空态（`GET.tree.empty.json`）；`menus` 增加**条目内嵌任务摘要**的列表、单栏详情态（`GET.detail.json`）、空态（`GET.list.empty.json`）。同步补齐 openapi `Category/Menu/MenuItem/TaskBrief` schema（详见 §1.2）。
- ✅ **周期任务全字段样例**（E-16 依赖，新形态）：`GET.list.json` 覆盖 DAILY 无限、WEEKLY+`byDay`+`count`、MONTHLY+`until`、`cycleLastCompleted` 已推进/未开始各一；`GET.calendar.json` 演示展开实例。
- ✅ **写操作 / 错误态覆盖**（2026-09-29，修订 0302/P3、P5）：写态 24 份（tasks/tags/categories/menus/files/users）+ 子任务读态；错误态 9 份 + `_errors/README.md`（对齐 `02 §1.4`）；同步补 openapi `PUT/DELETE /menus/{id}` 等 3 端点。详见 §1.3。
- ✅ **旧接口形态**（`legacy-api/`，2026-09-28）：线上抓包结构 + 合成数据，驱动 `../screenshots/` 黄金截图；真实密钥/口令已脱敏（见 `legacy-api/README.md`）。
- ⏳ 待办：`users/GET.index.json` 按 `deptId/scope` 过滤子集（MSW handler 内过滤）；若确认需要「按 id 取单分类/单栏」独立接口，同步改 `02 §3.x` + openapi。
