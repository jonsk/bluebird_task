# frontend/ — 前端工程（Vue 3 + TS + Vite + Element Plus + Tailwind）

> **当前阶段：M1–M5 视图与组件已落地（M0 脚手架 + 业务实现）。** 技术栈/工程结构/基线冻结详见 `../Task/03前端模块详细设计.md`。
>
> 已实现：Vite 5 + Vue 3 + TS(strict) + **Element Plus（`unplugin-auto-import` + `unplugin-vue-components` 按需引入）** + Tailwind(preflight off) + Pinia + Vue Router；`openapi-typescript` 生成类型（禁手改）；`api/http.ts`（ApiResult 解包/错误码/20005 续签）；task/meta/auth/app/menu store；路由守卫（六大视图→scope 映射、roles→403）；LoginView/OidcSuccessView/DefaultLayout/TaskView/CalendarView/Admin(Org/Audit)/Error 视图；BB 组件（BbFilterRail/BbCategoryTree/BbTaskCard/BbTaskList/BbTaskComposer/BbTaskDetailPanel/BbMiniCalendar/BbTagConfig/BbAttachmentList/BbPriorityTag 等）+ `v-permission`；**MSW 直连基线 fixtures**（dev/test，生产剔除）。
>
> 验证：`pnpm typecheck`、`pnpm test`（13/13）、`pnpm test:e2e`（28/28）、`pnpm build`；DoD：`dist/` 无 `mockServiceWorker.js`（R14）。
>
> **旧系统 UI 对齐（2026-09-30）**：对照旧系统 `bluebird_task_Front` + 线上 `http://10.14.37.187:8081/` 的实测几何/计算样式全面对齐（去顶部 header、左栏 300px、卡片 62px、右栏 360px、内联详情面板、旧色板）。刻意偏离见下节。

## 左栏筛选（分类树 / 自定义栏）

- `BbFilterRail.vue`：左栏承载（标题块 `.left-title`(64px) + 六视图计数 + 内联自定义栏 + 分类树 + 底部 50px 管理入口），固定在 `--bb-sidebar-left-width`（**勿用会随内容撑宽的 flex 写法**，见下）。折叠由 `app.leftCollapsed` 控制（旧 `menu_show_flag`），主区左上角 `[data-test="rail-expand"]` 展开。
- `BbCategoryTree.vue`：`/categories` CRUD（范围 个人/部门/组织，ADR-015）；点节点 → 任务列表按 `categoryId` **子树**过滤。契约见 `../docs/frontend-baseline/contracts/BbCategoryTree.md`。
- **自定义栏**（原独立组件 `BbCustomMenu.vue` 已删除）：并入 `BbFilterRail.vue`，与六视图**同一 `ul` 内联行**（`[data-test="menu-row"]`，Notebook 图标 + hover 显 `重命名`/`删除` + 内联改名 `el-input.bb-menu-row__input`），列表末尾 `[data-test="menu-add"]`（旧「新增自定义任务栏」行）。`stores/menu.ts` 负责 `/menus` CRUD + 条目；点栏 → 切「全部任务」并按 `menuId` 过滤；卡片「添加到自定义任务栏」= `POST /menus/{id}/items`。
- **标签入口**（`BbTagConfig.vue`）：按旧站置于**主区顶栏** `.r-b-t-right`（不在左栏）。
- 筛选态存 `task` store（`categoryId`/`menuId`），`TaskView` 监听并重查；同类名/栏名以 chip 展示，可单独清除。

## Mock 与 E2E（契约同源）

- **MSW 直连 fixtures**：`src/mocks/fixtures.ts` 用 `import.meta.glob` 注册 `../docs/frontend-baseline/fixtures/api/**`（键 `<目录>/<METHOD>.<name>`）；`src/mocks/handlers.ts` 读态原样返回、错误分支复用 `_errors/`、写态在内存副本变更（分类/自定义栏为**内存 CRUD**）。覆盖度断言见 `tests/unit/mocks-fixtures.spec.ts`。
- **E2E / 视觉回归**（Playwright Test）：`e2e/filter-rail.spec.ts`（E-08/E-10 左栏筛选）+ `e2e/task-flow.spec.ts`（E-01..E-17）+ `e2e/visual.spec.ts`（9 张，基线入库于 `e2e/__screenshots__/`）。
  - `pnpm test:e2e` / `pnpm test:e2e:update`（更新基线）；本地默认复用系统 **Edge**（`channel: msedge`，免下载），CI 用随包 Chromium（`playwright install chromium`）。
  - 视口 1440×900、时区 Asia/Shanghai、`page.clock` 冻结时间 → 确定性。
  - 选择器约定：下拉项用 `.el-dropdown-menu__item:visible`（所有节点的 dropdown 菜单都常驻 DOM）；树的点选用 `.cat-node__label`（徽标已 `pointer-events:none`）；左栏管理入口 `[data-test="nav-calendar|nav-adminOrg|nav-adminAudit"]`；不使用 toast（瞬态）作为断言依据。
  - `settle()`（`e2e/helpers.ts`）会等左栏**异步**数据落地（`.layout__count` × 6、`.cat-node` × 7、`[data-test="menu-row"]` × 3）后才截图/断言——否则与 MSW 响应竞态，基线会缺左栏内容。
  - ⚠️ 视觉门禁为「默认逐像素 `threshold` + `maxDiffPixelRatio: 0.02`」，**浅色文本差异可能不触发失败**；改动左栏/布局后请**显式重生基线**（`pnpm test:e2e:update`，必要时先删旧 PNG）。

## 旧系统 UI 对齐的刻意偏离（2026-09-30）

对照旧系统实测后，新前端**有意不复制**旧站缺陷、并按新架构调整了少数落点（旧→新的取舍）：

| # | 旧站行为 | 新前端做法 | 原因 |
|---|---|---|---|
| 1 | 详情面板为右栏内联的 `.dialog-right-box` | 同左（`BbTaskDetailPanel`），**非**覆盖式抽屉 | 与旧站一致；`BbTaskDetailDrawer.vue` 已删除 |
| 2 | 日历常驻右栏（下方即详情面板） | 同左（`BbMiniCalendar`）；`/calendar` 路由保留，由左栏底部日历图标进入 | 保留独立日历视图入口 |
| 3 | 「标签」入口在主区顶栏 `.r-b-t-r-item` | 同左（`BbTagConfig` 挂主区顶栏） | 与旧站一致，不占用左栏空间 |
| 4 | 左栏底部为空块 | 50px 图标条：日历 / 组织管理（用户+部门左树右表）/ 日志 | 管理页否则不可达（旧站另有路由）；`UserManage`+`DeptManage` 合并为 `OrgManage`（左树右表） |
| 5 | 新建任务配置行为**点图标→弹层**（`dropdownSetDate/Tips/Each`） | `BbTaskComposer` 截止/提醒/重复同左（`el-dropdown` 弹层，快捷项 + 日历/自定义），其余（优先级/指派/标签/分类）为内联选择 | 对齐旧 `addTaskBlock.vue` 交互 |
| 6 | 「添加步骤」可内联创建子任务 | 同左：详情面板「添加步骤」输入框已启用，`POST /tasks` 传 `parentId`（子任务入详情 `subtasks`） | `TaskCreateReq/TaskCmd` 已加 `parentId` |
| 7 | 登录页背景为 `login-background.jpg` | 同色系 CSS 渐变 | 图片素材未随仓迁移 |
| 8 | `provider=LOCAL` 时仍显示 企业微信/账密 Tabs | 仅 `provider!=='LOCAL'` 才渲染 Tabs | 单一 Tab 无意义；默认本地账密 |
| 9 | 1440 视口下主栏横向溢出（947.8 + 360 > 1140） | **不复制**：主栏 740 + 右栏 360 = 1100 | 旧站布局缺陷 |

> 旧系统权威依据：`bluebird_task_Front/src/layoutNew/**`、`assets/styles/todolist.scss`；实测 spec 见 `scripts/golden-capture/`（`probe.mjs`/`capture-components.mjs` 几何与计算样式提取）。

## 规划结构（对应 03 §3.2 / 01 §6）

```
frontend/
├── index.html  package.json  Dockerfile
├── vite.config.ts  tailwind.config.ts  tsconfig.json  playwright.config.ts
├── .env.development  .env.production
├── src/
│   ├── api/            # http.ts + 按域请求（auth/task/tag/category/menu/user/dept/file/audit）
│   ├── types/api.d.ts  # openapi-typescript 生成（禁手改）
│   ├── components/bb/  # BB 原子组件
│   ├── mocks/          # fixtures.ts（直连基线 fixtures）+ handlers.ts + browser.ts
│   ├── layouts/ router/ stores/ views/ utils/
│   └── main.ts  App.vue
├── tests/unit/         # Vitest 单测
└── e2e/                # Playwright（E2E + 视觉基线 __screenshots__/，入库）
```

## 契约与基线（仓根共享，不在此目录）

- `../docs/api/openapi.yaml` — API 契约单一事实源（前后端共享）
- `../docs/frontend-baseline/` — 基线冻结产物（行为契约 / 黄金截图 / E2E 清单 / fixtures）

## 工程初始化（随 M0 脚手架落地，此目录当前无代码）
