# frontend/ — 前端工程（Vue 3 + TS + Vite + Element Plus + Tailwind）

> **当前阶段：M0 脚手架已落地。** 技术栈/工程结构/基线冻结详见 `../Task/03前端模块详细设计.md`（审核中）。
>
> 已实现：Vite 5 + Vue 3.4 + TS(strict) + Element Plus + Tailwind(preflight off) + Pinia + Vue Router；`openapi-typescript` 生成类型（禁手改）；`api/http.ts`（ApiResult 解包/错误码/20005 续签）；auth/app store；路由守卫（六大视图→scope 映射、roles→403）；LoginView/OidcSuccessView/DefaultLayout/Error 视图；BB 原子 BbButton/BbInput/BbEmpty + `v-permission`；MSW（dev/test，生产剔除）。
>
> 验证：`pnpm typecheck`、`pnpm test`（5/5）、`pnpm build`；DoD：`dist/` 无 `mockServiceWorker.js`（R14）。

## 规划结构（对应 03 §3.2 / 01 §6）

```
frontend/
├── index.html  package.json  Dockerfile
├── vite.config.ts  tailwind.config.ts  tsconfig.json
├── .env.development  .env.production
├── src/
│   ├── api/            # http.ts + 按域请求（auth/task/tag/category/menu/user/dept/file/audit）
│   ├── types/api.d.ts  # openapi-typescript 生成（禁手改）
│   ├── components/bb/  # BB 原子组件
│   ├── components/biz/ # BB 业务组件
│   ├── layouts/ router/ stores/ views/ utils/
│   └── main.ts  App.vue
└── tests/              # Vitest（单测）+ Playwright（E2E/视觉）
```

## 契约与基线（仓根共享，不在此目录）

- `../docs/api/openapi.yaml` — API 契约单一事实源（前后端共享）
- `../docs/frontend-baseline/` — 基线冻结产物（行为契约 / 黄金截图 / E2E 清单 / fixtures）

## 工程初始化（随 M0 脚手架落地，此目录当前无代码）
