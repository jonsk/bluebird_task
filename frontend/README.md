# BlueBird Task — Frontend

BlueBird Task 的 Web 前端，基于 Vue 3 + TypeScript + Vite 构建。

## 技术栈

- Vue 3（Composition API + `<script setup>`）
- TypeScript（strict）
- Vite 5
- Pinia / Vue Router
- Element Plus + Tailwind CSS
- MSW（开发与测试环境的接口 Mock）
- Vitest / Playwright

## 环境要求

- Node.js 20+
- pnpm 12+

## 开发

```bash
pnpm install
pnpm dev
```

默认访问 `http://localhost:5173`。开发环境默认启用 MSW，接口数据来自
`src/mocks/fixtures/api/**`，因此不启动后端也能浏览完整界面。

连接本地后端时，新建 `.env.development.local`：

```dotenv
VITE_USE_MOCK=false
```

Vite 会把 `/api` 代理到 `http://localhost:8080`。

## 常用脚本

| 命令 | 说明 |
|---|---|
| `pnpm dev` | 启动开发服务器 |
| `pnpm build` | 类型检查并构建生产产物到 `dist/` |
| `pnpm preview` | 预览构建产物 |
| `pnpm typecheck` | TypeScript 类型检查（`vue-tsc --noEmit`） |
| `pnpm test` | 运行 Vitest 单元测试 |
| `pnpm test:e2e` | 运行 Playwright 端到端测试 |
| `pnpm lint` | ESLint 检查 |
| `pnpm gen:types` | 由 `docs/api/openapi.yaml` 生成 `src/types/api.d.ts` |

## 目录结构

```
src/
├── api/          # 按业务域封装的请求（http.ts 统一处理鉴权与错误码）
├── components/   # 通用组件（bb/）、业务组件（note/、admin/）
├── composables/  # 组合式函数
├── directives/   # 自定义指令（如 v-permission）
├── layouts/      # 页面布局
├── mocks/        # MSW 处理器与接口 fixtures
├── plugins/      # 插件注册
├── router/       # 路由与访问守卫
├── stores/       # Pinia 状态
├── styles/       # 全局样式与主题变量
├── types/        # 类型定义（api.d.ts 为自动生成，请勿手改）
├── utils/        # 工具函数
└── views/        # 页面视图
tests/unit/       # Vitest 单元测试
e2e/              # Playwright 端到端测试
```

## 接口 Mock

- `src/mocks/fixtures.ts` 通过 `import.meta.glob` 注册 `src/mocks/fixtures/api/**`
- `src/mocks/handlers.ts` 负责读写逻辑：读请求原样返回 fixtures，写请求在内存副本上变更
- 生产构建会自动剔除 Mock

新增接口时，请同时更新 `docs/api/openapi.yaml` 并执行 `pnpm gen:types`，
再补充对应的 fixtures 与 handler。

## 端到端测试

E2E 用例位于 `e2e/`，覆盖任务、工作日志、权限与左栏筛选等主要流程。
测试通过 MSW 直连 fixtures，无需后端；本地默认复用系统 Microsoft Edge，
CI 使用 Chromium。用例以 DOM、文案和路由断言为主，不做截图回归。

## 相关文档

- [项目总览](../README.md)
- [API 契约](../docs/api/openapi.yaml)
