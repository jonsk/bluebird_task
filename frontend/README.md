# frontend/ — 前端工程（Vue 3 + TS + Vite + Element Plus + Tailwind）

> **当前阶段：M0 占位。** 技术栈/工程结构/基线冻结详见 `../Task/03前端模块详细设计.md`（审核中）。代码随 M0 初始化开始。

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
