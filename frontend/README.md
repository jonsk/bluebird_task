# frontend/ — 前端工程（Vue 3 + TS + Vite + Element Plus + Tailwind）

> **当前阶段：M1–M5 视图与组件已落地（M0 脚手架 + 业务实现）。** 技术栈/工程结构/基线冻结详见 `../Task/03前端模块详细设计.md`。
>
> 已实现：Vite 5 + Vue 3 + TS(strict) + **Element Plus（`unplugin-auto-import` + `unplugin-vue-components` 按需引入）** + Tailwind(preflight off) + Pinia + Vue Router；`openapi-typescript` 生成类型（禁手改）；`api/http.ts`（ApiResult 解包/错误码/20005 续签）；task/meta/auth/app store；路由守卫（六大视图→scope 映射、roles→403）；LoginView/OidcSuccessView/DefaultLayout/TaskView/CalendarView/Admin(User/Dept/Audit)/Error 视图；BB 组件（BbTaskCard/BbTaskList/BbTaskComposer/BbTaskDetailDrawer/BbAttachmentList/BbPriorityTag 等）+ `v-permission`；**MSW 直连基线 fixtures**（dev/test，生产剔除）。
>
> 验证：`pnpm typecheck`、`pnpm test`（13/13）、`pnpm test:e2e`（24/24）、`pnpm build`；DoD：`dist/` 无 `mockServiceWorker.js`（R14）。

## Mock 与 E2E（契约同源）

- **MSW 直连 fixtures**：`src/mocks/fixtures.ts` 用 `import.meta.glob` 注册 `../docs/frontend-baseline/fixtures/api/**`（键 `<目录>/<METHOD>.<name>`）；`src/mocks/handlers.ts` 读态原样返回、错误分支复用 `_errors/`、写态在内存副本变更。覆盖度断言见 `tests/unit/mocks-fixtures.spec.ts`。
- **E2E / 视觉回归**（Playwright Test）：`e2e/task-flow.spec.ts`（E-01..E-17）+ `e2e/visual.spec.ts`（8 张，基线入库于 `e2e/__screenshots__/`）。
  - `pnpm test:e2e` / `pnpm test:e2e:update`（更新基线）；本地默认复用系统 **Edge**（`channel: msedge`，免下载），CI 用随包 Chromium（`playwright install chromium`）。
  - 视口 1440×900、时区 Asia/Shanghai、`page.clock` 冻结时间 → 确定性。

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
