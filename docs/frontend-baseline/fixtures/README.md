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
└── api/                            # 按接口路径镜像，便于 MSW/Playwright 直读
    ├── auth/POST.login.json
    ├── tasks/
    │   ├── GET.list.json           # 六大视图列表（含周期任务；E-03/E-16）
    │   ├── GET.detail.json         # 含子任务
    │   ├── GET.count.json          # 各视图计数
    │   └── GET.calendar.json       # 日历区间（周期实例展开；E-16）
    ├── users/
    │   ├── GET.index.json          # 人员选择器（手机号脱敏）
    │   └── GET.tree.json           # 组织树
    ├── tags/GET.list.json
    ├── categories/GET.tree.json
    ├── menus/GET.list.json
    └── audit/
        ├── GET.logins.json
        └── GET.operates.json
```

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
| `CountVO` | `{day,week,joined,assigned,collect,all}` | 六大视图计数（recurring 按展开实例） |

## 5. MSW / Playwright 契约

- **新前端（MSW）**：`src/mocks/handlers.ts` 的每个 `http.get('/api/v1/tasks', ...)` 返回 `fixtures/api/tasks/GET.list.json`（按 scope 过滤出对应子集以复用同一份数据）。
- **旧前端（Playwright）**：`page.route('**/api/**', route => route.fulfill({ json: <对应 fixture> }))`，路径与 MSW handler 对齐。
- **版本化**：fixtures 变更需随契约（openapi.yaml）同步；回归时始终引用**同一 commit** 的 fixtures，避免新旧前端用不同版本数据比对。

## 6. 当前状态

- ✅ **规范 + 首批 JSON 样例已产出**（2026-09-28）：`tasks/GET.list|detail|count|calendar.json`、`users/GET.index|tree.json`、`tags/`、`categories/`、`menus/`、`audit/`、`auth/POST.login.json`，字段均对齐 `../../api/openapi.yaml`。
- ✅ **周期任务全字段样例已含**（E2E E-16 依赖）：`GET.list.json` 覆盖 DAILY 无限、WEEKLY+`byDay`+`count`、MONTHLY+`until`、`cycleLastCompleted` 已推进/未开始各一；`GET.calendar.json` 演示展开实例（同 id 多渠道多实例）。
- ⏳ 待办：随 openapi 演进补齐 `files/`；`users/GET.index.json` 按 `scope` 过滤子集（MSW handler 内过滤）；黄金截图证据（见 `../screenshots/README.md`、M2）。
