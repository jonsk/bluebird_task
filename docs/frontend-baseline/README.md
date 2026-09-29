# 前端基线冻结（Frontend Baseline Freeze）

> 目标：把旧前端 `bluebird_task_Front`（RuoYi-Vue 3.8.8）里**手写组件的隐形行为**显式化为**可执行基线**，作为重写（BB 组件）的验收依据。
> 方法论见 `../../Task/03前端模块详细设计.md §2`（三步法：① 基线冻结 → ② 设计系统重构 → ③ 对照验收）。
> 本目录为**仓根共享**，不置于 `frontend/` 下。

## 1. 目录结构

```
docs/frontend-baseline/
├── README.md           # 本文：总览 / 清单状态 / 验收依据 / 流程 / 历史
├── contracts/          # 契约 30 份 = 16 业务（行为契约）+ 14 原子（设计/风格契约）
│   ├── README.md       # 索引 + 模板 + 计数口径(L3) + 旧→新接口映射(L4) + 完成度
│   ├── Bb*.md          # 16 业务：TaskCard / Composer / SubtaskList / CalendarCard /
│   │                   #   DatePicker / RemindSelect / RepeatSelect / UserSelect /
│   │                   #   CategoryTree / OrgTree / TagConfig / TaskList /
│   │                   #   TaskDetailDrawer / TaskMetaLine / AttachmentList / ParticipantList
│   └── Bb*.md          # 14 原子：Button / Input / Select / Modal / Drawer / Tag / Icon /
│                       #   Tree / Table / Pagination / Empty / Tooltip / Loading / Confirm
├── e2e/
│   └── scenarios.md    # E2E 场景清单（先对旧前端跑通作基线，再对新建前端验收）
├── fixtures/
│   ├── README.md       # fixtures 规范 + 数据字典 + MSW/Playwright 共用约定
│   ├── api/            # 新接口形态（MSW）：读态 + 写态 + 错误态样例
│   └── legacy-api/     # 旧接口形态（Playwright 注入）：dataset.json + README
├── screenshots/
│   ├── README.md       # 黄金截图/录屏采集规程 + 状态
│   ├── .gitkeep        # 黄金截图（图片/录屏不入库，见 .gitignore；由 harness 复现）
│   └── generated/      # 运行期生成的对比产物（不入库，见 .gitignore）
└── accessibility/
    └── .gitkeep        # （预留）无障碍/语义检查结果
```

> **采集脚本（harness）位置（修订 0302/P1）**：Playwright 采集脚本在**仓库根** `scripts/golden-capture/`（**非**本目录下；已入 git：`capture.mjs` / `capture-components.mjs` / `capture-states.mjs` / `verify.mjs` / `lib.mjs` / `probe.mjs`）。复现：`cd scripts/golden-capture && npm i && node capture.mjs`。详见 `../../scripts/golden-capture/README.md`。

> 关联设计文档（相对 `docs/frontend-baseline/`）：`../../Task/01..04*.md`（仓库内权威副本，见 `../../Task/README.md`）、API 契约 `../../docs/api/openapi.yaml`。

## 2. 冻结原则（对照 03 §2.3）

1. **行为契约必须基于旧源码导出**，并在旧前端可运行期间补齐「录屏/截图」证据，禁止仅靠推断。
2. **同一份 fixtures**：旧前端用 Playwright `page.route('**/api/**', ...)` 在浏览器层拦截旧接口返回 fixture；新前端用 MSW 加载**同一份** fixture（JSON 跨新旧共享、版本化），保证同数据对比（03 §2.3.2 修订 M3）。
3. **黄金截图**：固定视口（默认 1440×900）+ 固定 Mock 数据渲染，版本化管理，新前端同数据同视口 → 结构化/像素对比（03 §2.5.1）。
4. **验收依据**：`DoD`（03 §2.8）= 单测绿 + E2E 绿 + 视觉对比通过 + 无 TS/console 错 + 评审通过；且生产构建 `dist` 不含 `mockServiceWorker.js`（R14）。

## 3. 组件映射与契约状态（M0 出口标准：契约完成度为放行条件，见 03 §8）

| 类别 | 数量 | 契约文档 | 状态 |
|---|---|---|---|
| 业务组件（**行为契约**，基于旧源码） | 16 | `contracts/Bb*.md` | ✅ 全部导出 |
| 原子组件（**设计/风格契约**，EP 薄封装） | 14 | `contracts/Bb*.md` | ✅ 全部导出 |
| **合计** | **30** | 索引见 `contracts/README.md` | ✅ **30/30** |

> 完整清单、计数口径与交付形态见 `contracts/README.md`「契约范围与计数口径(L3)」与「组件契约清单」。**契约完成度是 M0 出口硬标准**（03 §8 / 修订 R15）。

## 3.1 M0 出口进度（03 §8）

| 出口项 | 目标 | 现状 |
|---|---|---|
| 业务行为契约 | 16 | ✅ 16（`contracts/Bb*.md`） |
| 原子设计契约 | 14 | ✅ 14（`contracts/Bb*.md`，风格契约） |
| E2E 场景清单 | 已列 | ✅ 17 场景（`e2e/scenarios.md`） |
| fixtures JSON | 有样例 | ✅ 双形态：`fixtures/api/**`（新接口：读态 + **写态** + **错误态** + 分类/栏详情态与空态）+ `fixtures/legacy-api/**`（旧接口） |
| 黄金截图/录屏证据 | 采集 | ✅ **38 张 PNG（19 基线 + 19 空/异常态）+ 1 录屏**（`screenshots/`，fixtures 注入，确定性；由仓根 `scripts/golden-capture/` 复现） |
| API 契约源 | 就位 | ✅ `../../docs/api/openapi.yaml` 初版（M1） |
| 引用可解析 | 全绿 | ✅ 设计文档已入仓 `../../Task/`（M1） |

## 4. 验收链路（前后端如何用本基线）

```
旧前端(可运行) ──固定 fixtures──▶ 黄金截图 + E2E 基线（本次冻结）
                                    │
新前端(重建) ──MSW 同 fixtures──▶ 对照验收（03 §2.5）
        │                            │
        └── 行为契约(contracts/) ──▶ DbBbTaskCard 快照断言（03 §2.3.1 / R16）
```

## 5. 历史与变更记录

- **2026-09-28（M0 启动）**：建立本目录骨架；基于旧源码导出首批 5 份行为契约（TaskCard / addTaskBlock / childTaskList / CalendarCard / dropdownSetDate）；建立 fixtures 规范与 E2E 场景清单框架。
- **2026-09-28（0301 审核修订）**：
  - **M1**（引用断裂/布局漂移）✅：设计文档入仓 `Task/01..04`（`Task/README.md`）；建 `docs/api/openapi.yaml` 初版；修正 `e2e/`、`fixtures/` 的 `../../Task/` 深度错误。
  - **L1**（fixtures 未产出）✅：产出首批 `fixtures/api/**`（含周期任务全字段样例）。
  - **L2**（行号漂移）✅：`contracts/README` 加行号约定 + 5 份契约顶部注记。
  - **L3**（30 契约计数口径）✅：`contracts/README` 明确「16 业务行为契约 + 14 原子设计契约」两口径。
  - **L4**（旧接口废弃映射）✅：`contracts/README` 增「旧→新接口映射表」（据旧 `record.js` 实源）。
  - **M2**（证据缺口）⏳：新增 `screenshots/README.md` 采集规程；黄金截图/录屏仍待隔离环境采集（M0 内完成）。
  - 审核原文：`Task/0301审核_前端基线冻结.md`（评审工作区）。
- **2026-09-28（M2 证据采集完成）**：
  - 旧前端隔离环境跑通（`http://10.14.37.187:8081/`，登录 `admin`）。
  - 新增 `fixtures/legacy-api/`（旧接口形态数据集，脱敏）与 `scripts/golden-capture/`（Playwright harness）。
  - 采集 **8 张黄金截图 + 1 段录屏** → `screenshots/`（我的一天/未来7天/我@Ta/分配给我/我的收藏/全部任务/登录/完成交互）。
  - 线上库任务为空，故用固定 fixtures 注入渲染（确定性、无真实数据）；`verify.mjs` 断言卡片数通过。
  - **组件级补充采集**（`capture-components.mjs`）：左栏、日历卡、新增任务块（收起/展开）、四个下拉（日期/提醒/重复/标签）、标签配置、人员选择对话框，共 +10 张；截图合计 19 张 + 1 录屏。
- **2026-09-29（契约补齐 + 空/异常态）**：
  - **契约 30/30 达成（M0 出口硬标准）**：新增 11 份业务行为契约（RemindSelect/RepeatSelect/UserSelect/CategoryTree/OrgTree/TagConfig/TaskList/TaskDetailDrawer/TaskMetaLine/AttachmentList/ParticipantList）；新建 14 份原子设计契约（Button/Input/Select/Modal/Drawer/Tag/Icon/Tree/Table/Pagination/Empty/Tooltip/Loading/Confirm，统一 `--bb-*` 令牌）。
  - **空态/异常态证据**（`scripts/golden-capture/capture-states.mjs`）：六大视图 `state-empty-*`（6）、`state-error-*`（6，`body.code=500` → ElMessage）、`state-httperr-*`（6，HTTP 500 静默）、登录失败（1），共 +19 张。
  - **缺陷留痕**：旧 `utils/request.js` 对 HTTP 非 2xx **静默 reject 无提示**；错误态 ElMessage **累积堆叠**（见 `screenshots/README.md`）。
- **2026-09-29（0302 审核修订 · 实操验证层）**：真实登录旧系统抓包复核（legacy-api 形态 100% 吻合）。
  - **P1 澄清**（采集脚本"缺失"）：复核为**误报**——harness 在**仓根** `scripts/golden-capture/`（已入 git，非 `frontend-baseline/` 下）；已在 §1 结构注记 + §6 关联文档显式标注路径，可复现性成立。
  - **P2 修正**（截图计数）：§3.1 由「19+12」更正为**38 张 PNG（19 基线 + 19 空/异常态）+ 1 录屏**；`screenshots/README.md` 同步。
  - **P3 补齐**（写操作 fixtures）：新增 24 份写态（tasks/tags/categories/menus/files/users）+ `tasks/GET.subtasks.json`；对齐 openapi 补 `PUT/DELETE /menus/{id}`、`DELETE /menus/{id}/items/{itemId}`。
  - **P5 补齐**（错误态 fixtures）：新增 `fixtures/api/_errors/` 9 份（对齐 `02 §1.4` ErrorCode）+ README。
  - **P4**（`/getInfo`、`/getRouters` 不可用）：信息项，baseline 用 `/admin/user/myself`（已验证），无需动作。

## 6. 关联文档

- 前端设计：`../../Task/03前端模块详细设计.md`
- 旧组件清单/映射：`../../Task/03前端模块详细设计.md §1.1`、`§2.9`
- API 契约源：`../../docs/api/openapi.yaml`
- 采集脚本（harness）：`../../scripts/golden-capture/README.md`
