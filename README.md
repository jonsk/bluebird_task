# BlueBird Task（蓝鸟任务）

私有化任务 / 待办管理系统（重构替代旧 `bluebird_task_Front` + `bluebird_task_End`，单制品 jar 交付）。

## 仓库状态

> **当前阶段：任务模块 + 工作日志（Note）模块已完成前端全量实现、后端 note 模块入库，验收与测试基线冻结。**
>
> 已完成的验证基线（可直接复跑，见下节「质量门禁」）：
> - 前端类型检查 `vue-tsc --noEmit` 零错误；单测 **19 文件 / 121 例**（Vitest）
> - e2e **63 例**（Playwright：任务 18 / 日志 20 / 权限 5 / 视觉 19 / 左栏 1），含 1280×800 视觉基线
> - 后端 `mvn test` **42 例**（含 Note 授权矩阵、细分流程、全文/引用/附件守卫）
> - Note §9 接口验收矩阵脚本 **117 例主跑 + 4 例开关关闭** 全通过（0 例未自动化）（`scripts/acceptance/`）

## 技术要点（摘要，详见 `Task/04决策记录(ADR).md` / `01蓝鸟重构方案.md`）

- **Monorepo**：`backend/`（Spring Boot 3 + Java 21 + MyBatis-Plus 3.5.x + **SQLite**）+ `frontend/`（Vue 3 + TS + Vite + Element Plus + Tailwind）。**无 Redis、无缓存**（ADR-016）。
- **单制品（ADR-009）**：前端 `dist` 打包期注入 `backend/src/main/resources/static/`，产出**唯一 `bluebird-task.jar`**，不使用 Nginx，SPA 回退由后端 `SpaForwardController` 兜底。**无外部 DB/缓存服务**（SQLite 嵌入式单文件）。
- **认证（ADR-006/008）**：默认 `provider=LOCAL`（仅账号密码）；OIDC / 企微为可选（条件装配，默认关闭）。
- **周期任务**：计算式展开（`cycle_rule` TEXT(JSON) + `cycle_last_completed`），不物化多行。
- **前端质量（ADR-007/011）**：基线冻结（行为契约/黄金截图/E2E/fixtures）+ 设计系统（BB 组件）+ 契约 Mock（MSW）。

## 目录结构（单一权威：`Task/01蓝鸟重构方案.md §6`）

```
bluebird-task/
├── docs/                        # 仓根共享文档（架构/数据模型/openapi/部署/基线）
│   ├── api/openapi.yaml         # API 契约单一事实源（前后端共享）
│   ├── Note模块前端开发方案.md    # Note 模块方案 + §9 验收矩阵 + §12 as-built
│   └── frontend-baseline/       # 前端基线冻结产物（行为契约/黄金截图/E2E/fixtures）
├── deploy/                      # docker-compose（单服务：backend 单制品；无 pg/redis）
├── backend/                     # 后端工程（Spring Boot）
├── frontend/                    # 前端工程（Vue 3）
└── scripts/
    ├── check-contract.mjs       # 契约一致性门禁（openapi ↔ docs ↔ ErrorCode/fixtures）
    └── acceptance/              # Note §9 接口验收矩阵脚本（可接 CI）
```

## 快速开始

本地依赖**无**（SQLite 嵌入式、无缓存，ADR-016）。

```powershell
# 后端：自动创建 backend/data/bluebird.db，监听 8080
cd backend; mvn -o spring-boot:run

# 前端：联调真实后端（VITE_USE_MOCK=false）；默认 mock（MSW 直连 fixtures）
cd frontend; pnpm install; pnpm dev      # http://localhost:5173

# 构建单制品：前端 build → 拷贝 dist → backend/src/main/resources/static/ → mvn verify
```

## 质量门禁（提交前必跑）

```powershell
cd frontend
pnpm exec vue-tsc --noEmit     # 类型检查
pnpm test                      # 单测（Vitest，19 文件 / 121 例）
pnpm test:e2e                  # e2e + 视觉回归（Playwright，本地复用系统 Edge）
pnpm test:e2e:update           # 仅在**有意**改动布局时重生成基线，并在 PR 中说明

cd ..\backend
mvn -o test                    # 后端测试（42 例）

# Note §9 接口验收矩阵（需后端已启动；开关场景需以 MANAGER_CAN_READ_SUBORDINATE=false 重启）
cd ..\scripts\acceptance
py seed_accounts.py; py matrix_main.py   # 退出码 0 = 全通过
```

CI（`.github/workflows/ci.yml`）：契约一致性 + 密钥扫描 + 前端类型/单测/构建 + 后端 `mvn verify` + e2e；验收矩阵为手动触发（`workflow_dispatch` → `run_acceptance=true`）。

## 设计文档（权威源）

- `Task/01蓝鸟重构方案.md` — 总方案
- `Task/02后端模块详细设计.md` — 后端详设
- `Task/03前端模块详细设计.md` — 前端详设
- `Task/04决策记录(ADR).md` — 架构决策记录
- 本仓 `docs/` — 定稿镜像与模块级方案（`Note模块前端开发方案.md` 含验收矩阵与 as-built）

## License

Apache-2.0（见 `LICENSE`）。
