# 前端基线冻结（Frontend Baseline Freeze）

> 目标：把旧前端 `bluebird_task_Front`（RuoYi-Vue 3.8.8）里**手写组件的隐形行为**显式化为**可执行基线**，作为重写（BB 组件）的验收依据。
> 方法论见 `../../Task/03前端模块详细设计.md §2`（三步法：① 基线冻结 → ② 设计系统重构 → ③ 对照验收）。
> 本目录为**仓根共享**，不置于 `frontend/` 下。

## 1. 目录结构

```
docs/frontend-baseline/
├── README.md           # 本文：总览 / 清单状态 / 验收依据 / 流程 / 历史
├── contracts/          # 行为契约（每个旧组件一份，见 03 §2.3.1 模板）
│   ├── README.md       # 契约索引 + 状态清单 + 模板
│   ├── BbTaskCard.md      （旧 TaskCard.vue）
│   ├── BbTaskComposer.md  （旧 addTaskBlock.vue）
│   ├── BbSubtaskList.md   （旧 childTaskList.vue）
│   ├── BbCalendarCard.md  （旧 CalendarCard.vue）
│   └── BbDatePicker.md    （旧 dropdownSetDate.vue）
├── e2e/
│   └── scenarios.md    # E2E 场景清单（先对旧前端跑通作基线，再对新建前端验收）
├── fixtures/
│   └── README.md       # 固定 Mock 数据（fixtures）规范 + 数据字典 + MSW/Playwright 共用约定
├── screenshots/
│   ├── .gitkeep        # 黄金截图（版本化，Playwright 固定视口 1440×900 导出）
│   └── generated/      # 运行期生成的对比产物（不入库，见 .gitignore）
└── accessibility/
    └── .gitkeep        # （预留）无障碍/语义检查结果
```

## 2. 冻结原则（对照 03 §2.3）

1. **行为契约必须基于旧源码导出**，并在旧前端可运行期间补齐「录屏/截图」证据，禁止仅靠推断。
2. **同一份 fixtures**：旧前端用 Playwright `page.route('**/api/**', ...)` 在浏览器层拦截旧接口返回 fixture；新前端用 MSW 加载**同一份** fixture（JSON 跨新旧共享、版本化），保证同数据对比（03 §2.3.2 修订 M3）。
3. **黄金截图**：固定视口（默认 1440×900）+ 固定 Mock 数据渲染，版本化管理，新前端同数据同视口 → 结构化/像素对比（03 §2.5.1）。
4. **验收依据**：`DoD`（03 §2.8）= 单测绿 + E2E 绿 + 视觉对比通过 + 无 TS/console 错 + 评审通过；且生产构建 `dist` 不含 `mockServiceWorker.js`（R14）。

## 3. 组件映射与契约状态（M0 出口标准：契约完成度为放行条件，见 03 §8）

| 旧组件（源码路径） | 新组件 | 契约文档 | 状态 |
|---|---|---|---|
| `todolistModule/components/TaskCard.vue` | `BbTaskCard` | `contracts/BbTaskCard.md` | ✅ 已导出 |
| `todolistModule/components/addTaskBlock.vue` | `BbTaskComposer` | `contracts/BbTaskComposer.md` | ✅ 已导出 |
| `todolistModule/components/childTaskList.vue` | `BbSubtaskList` | `contracts/BbSubtaskList.md` | ✅ 已导出 |
| `todolistModule/components/CalendarCard.vue` | `BbCalendarCard` | `contracts/BbCalendarCard.md` | ✅ 已导出 |
| `todolistModule/components/dropdownSetDate.vue` | `BbDatePicker` | `contracts/BbDatePicker.md` | ✅ 已导出 |

> 完整 30 组件（14 原子 + 16 业务）契约导出为 M0 持续工作，**契约完成度是 M0 出口硬标准**（03 §8 / 修订 R15）；以上为已基于源码导出的首批 5 份，作为基线启动样例。

## 4. 验收链路（前后端如何用本基线）

```
旧前端(可运行) ──固定 fixtures──▶ 黄金截图 + E2E 基线（本次冻结）
                                    │
新前端(重建) ──MSW 同 fixtures──▶ 对照验收（03 §2.5）
        │                            │
        └── 行为契约(contracts/) ──▶ DbBbTaskCard 快照断言（03 §2.3.1 / R16）
```

## 5. 历史与变更记录

- **2026-09-28（M0 启动）**：建立本目录骨架；基于旧源码导出首批 5 份行为契约（TaskCard / addTaskBlock / childTaskList / CalendarCard / dropdownSetDate）；建立 fixtures 规范与 E2E 场景清单框架。待旧前端隔离运行后补充黄金截图与录屏证据、补齐其余 25 份契约。

## 6. 关联文档

- 前端设计：`../../Task/03前端模块详细设计.md`
- 旧组件清单/映射：`../../Task/03前端模块详细设计.md §1.1`、`§2.9`
- API 契约源：`../../docs/api/openapi.yaml`
