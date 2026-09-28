# BlueBird Task（蓝鸟任务）

私有化任务/待办管理系统（重构替代旧 `bluebird_task_Front` + `bluebird_task_End`，单制品 jar 交付）。

## 仓库状态

> **当前阶段：M0 仓库骨架 + 前端基线冻结启动。** 本仓库为**空库起步**，代码尚未开始；先有设计、后有实现。
> 设计文档位于上层 `Task/` 目录（评审中，尚未定稿进入本仓 `docs/`），完成审核后随 M0 落地同步复制。

## 技术要点（摘要，详见 `Task/04决策记录(ADR).md` / `01蓝鸟重构方案.md`）

- **Monorepo**：`backend/`（Spring Boot 3 + Java 21 + MyBatis-Plus 3.5.x + PostgreSQL 16）+ `frontend/`（Vue 3 + TS + Vite + Element Plus + Tailwind）。
- **单制品（ADR-009）**：前端 `dist` 打包期注入 `backend/src/main/resources/static/`，产出**唯一 `bluebird-task.jar`**，不使用 Nginx，SPA 回退由后端 `SpaForwardController` 兜底。
- **认证（ADR-006/008）**：默认 `provider=LOCAL`（仅账号密码）；OIDC / 企微为可选（条件装配，默认关闭）。
- **周期任务**：计算式展开（`cycle_rule` JSONB + `cycle_last_completed`），不物化多行。
- **前端质量（ADR-007/011）**：基线冻结（行为契约/黄金截图/E2E/fixtures）+ 设计系统（BB 组件）+ 契约 Mock（MSW）。

## 目录结构（单一权威：`Task/01蓝鸟重构方案.md §6`）

```
bluebird-task/
├── docs/                        # 仓根共享文档（架构/数据模型/openapi/部署/基线）
│   ├── api/openapi.yaml         # API 契约单一事实源（前后端共享，M0 产出）
│   └── frontend-baseline/       # 前端基线冻结产物（行为契约/黄金截图/E2E/fixtures）
├── deploy/                      # docker-compose（全量 / 仅依赖）
├── backend/                     # 后端工程（Spring Boot）
├── frontend/                    # 前端工程（Vue 3）
└── scripts/                     # 开发/备份脚本
```

## 快速开始（占位，M0 阶段补充）

- 本地依赖：见 `deploy/docker-compose.dev.yml`（pg + redis 起依赖）。
- 构建单制品：`frontend pnpm build` → 拷贝 `dist` 至 `backend/src/main/resources/static/` → `mvn verify`（详见 `Task/01 §11`）。

## 设计文档（当前权威源，评审中）

- `Task/01蓝鸟重构方案.md` — 总方案
- `Task/02后端模块详细设计.md` — 后端详设
- `Task/03前端模块详细设计.md` — 前端详设
- `Task/04决策记录(ADR).md` — 架构决策记录
- 评审留痕：`Task/02xx评审_*.md`

> 以上 `Task/` 目录在 M0 冻结后复制进本仓 `docs/`（`architecture.md`、`data-model.md` 等即其镜像）。

## License

Apache-2.0（见 `LICENSE`）。
