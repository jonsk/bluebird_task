# 前端基线冻结（Frontend Baseline Freeze）

> 目标：把旧前端 `bluebird_task_Front`（RuoYi-Vue 3.8.8）里**手写组件的隐形行为**显式化为**可执行基线**，作为重写（BB 组件）的验收依据。
> 方法论见 `../../Task/03前端模块详细设计.md §2`（三步法：① 基线冻结 → ② 设计系统重构 → ③ 对照验收）。
> 本目录为**仓根共享**，不置于 `frontend/` 下。

## 1. 目录结构

```
docs/frontend-baseline/
├── README.md           # 本文：总览 / 清单状态 / 验收依据 / 流程 / 历史
├── contracts/          # 契约 29 份 = 15 业务（行为契约；原 16，BbOrgTree 已废弃）+ 14 原子（设计/风格契约）
│   ├── README.md       # 索引 + 模板 + 计数口径(L3) + 旧→新接口映射(L4) + 完成度
│   ├── Bb*.md          # 15 业务：TaskCard / Composer / SubtaskList / CalendarCard /
│   │                   #   DatePicker / RemindSelect / RepeatSelect / UserSelect /
│   │                   #   CategoryTree / TagConfig / TaskList /
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

> **新前端 E2E / 视觉回归 harness（2026-09-29）**：在 `frontend/e2e/`（`frontend/playwright.config.ts`），对**新前端**（`vite` dev + MSW 直连 `fixtures/api/**`）跑通 E-01..E-17 与 8 张视觉回归基线。运行：`pnpm -C frontend test:e2e`（更新基线 `... test:e2e:update`；基线入库于 `frontend/e2e/__screenshots__/`）。

> 关联设计文档（相对 `docs/frontend-baseline/`）：`../../Task/01..04*.md`（仓库内权威副本，见 `../../Task/README.md`）、API 契约 `../../docs/api/openapi.yaml`。

## 2. 冻结原则（对照 03 §2.3）

1. **行为契约必须基于旧源码导出**，并在旧前端可运行期间补齐「录屏/截图」证据，禁止仅靠推断。
2. **同一逻辑数据集（两种序列化形态）**：旧前端用 Playwright `page.route('**/api/**', ...)` 拦截**旧接口**、加载 `fixtures/legacy-api/`；新前端用 MSW 拦截**新接口**、加载 `fixtures/api/`。两形态**字段/语义对齐、随契约版本化**，但**报文结构不同（`code==0` 信封 vs 裸 `Page`，非同一份字节级 JSON）**；口径详见 `fixtures/README §1.1`（0304/§5 统一）。
3. **黄金截图**：固定视口（默认 1440×900）+ 固定 Mock 数据渲染，版本化管理，新前端同数据同视口 → 结构化/像素对比（03 §2.5.1）。
4. **验收依据**：`DoD`（03 §2.8）= 单测绿 + E2E 绿 + 视觉对比通过 + 无 TS/console 错 + 评审通过；且生产构建 `dist` 不含 `mockServiceWorker.js`（R14）。

## 3. 组件映射与契约状态（M0 出口标准：契约完成度为放行条件，见 03 §8）

| 类别 | 数量 | 契约文档 | 状态 |
|---|---|---|---|
| 业务组件（**行为契约**，基于旧源码） | 15 | `contracts/Bb*.md` | ✅ 全部导出 |
| 原子组件（**设计/风格契约**，EP 薄封装） | 14 | `contracts/Bb*.md` | ✅ 全部导出 |
| **合计** | **29** | 索引见 `contracts/README.md` | ✅ **29/29**（`BbOrgTree` 0307 废弃后） |

> 完整清单、计数口径与交付形态见 `contracts/README.md`「契约范围与计数口径(L3)」与「组件契约清单」。**契约完成度是 M0 出口硬标准**（03 §8 / 修订 R15）。

## 3.1 M0 出口进度（03 §8）

| 出口项 | 目标 | 现状 |
|---|---|---|
| 业务行为契约 | 16 | ✅ 16（`contracts/Bb*.md`） |
| 原子设计契约 | 14 | ✅ 14（`contracts/Bb*.md`，风格契约） |
| E2E 场景清单 | 已列 | ✅ 17 场景（`e2e/scenarios.md`）；**旧基线已跑 9 项通过**（`scripts/golden-capture/e2e-baseline.mjs`）；**新前端 18/18 场景通过**（`frontend/e2e/`，27 项含 8 项视觉回归，2026-09-29） |
| fixtures JSON | 有样例 | ✅ 双形态：`fixtures/api/**`（新接口：读态 + **写态 21** + **错误态 19**（`02 §1.4` 全覆盖）+ auth 4 + 分类/栏详情态与空态）+ `fixtures/legacy-api/**`（旧接口）；**新前端 MSW 已直连 `fixtures/api/**`**（`frontend/src/mocks/fixtures.ts`）；详见 `fixtures/README §5/§6` |
| 黄金截图/录屏证据 | 采集 | ✅ **38 张 PNG（19 基线 + 19 空/异常态）+ 1 录屏**（`screenshots/`，fixtures 注入，确定性；由仓根 `scripts/golden-capture/` 复现）；**图片/录屏按策略不入库**（`.gitignore`，0304/D3）；新前端回归基线另存 `frontend/e2e/__screenshots__/`（**入库**） |
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

- **2026-09-29（E-08/E-10 补 UI + 后端契约/缺陷修复）**：
  - **左栏 UI 补齐**：新增 `frontend/src/components/bb/BbFilterRail.vue`（左栏承载：六视图计数 + 分类树 + 自定义栏，替代 `DefaultLayout` 内联 nav）、`BbCategoryTree.vue`（`GET/POST/PUT/DELETE /categories`；范围过滤 全部/个人/部门/组织、范围徽标、只读锁定、悬停增/改/删、拖拽改父级、点选联动）、`BbCustomMenu.vue`（`/menus` CRUD + 条目）；`BbTaskDetailDrawer` 增「移动到自定义栏」（`POST /menus/{id}/items`）；`TaskView` 增筛选条（分类/自定义栏 chip + 清除）。新增 `stores/menu.ts`；`task` store 增 `categoryId/menuId` 筛选态。
  - **契约扩展**：`GET /tasks` 增 `categoryId`（**子树**，ADR-015 共享分类）/`menuId`（仅本人栏）查询参数（`docs/api/openapi.yaml` + 后端 `TaskQuery`/`TaskQueryService`/`TaskController` + MSW 同步实现）。
  - **后端缺陷修复（本轮由新增测试与 DDL 对照暴露）**：
    1. `category` 表缺 `updated_at`，`GET /categories` 直接报 `no such column`（`CategoryNodeDTO`/openapi 已暴露该字段）→ 新增迁移 `V2__schema_drift_fix.sql` 补列（不改 `V1`，保护既有库 Flyway 校验和）。
    2. `JobLog` 实体字段 `jobName`/`msg` 与建表列 `job`/`error` 漂移（调度日志写入必失败）→ 实体加 `@TableField` 显式映射。
    3. `CategoryService` 写权限与 ADR-015 §3 漂移：`PERSONAL` 未校验创建者（**越权改/删他人个人分类**）、`DEPARTMENT` 误放宽为「本部门任意成员」（应为 创建者/部门负责人/ADMIN）、`ORG` 漏 `USER_MANAGER`；并修 `update` 未传 `deptId` 时误改挂载（ADMIN 改他人部门分类会报错/挪部门）。
  - **测试**：后端 `TaskFlowTest.filterByCategorySubtreeAndCustomMenu`（子树过滤 + 栏过滤 + 非本人栏 10004）；前端新增 `e2e/filter-rail.spec.ts`（E-10/E-10b/E-08）并重生 8 张视觉基线。
  - **布局缺陷修复**：左栏为 flex 项时 `min-width:auto` 被树内容撑宽（240 → 265px，主栏右移 25px）→ 固定 `flex: 0 0 var(--bb-sidebar-left-width)` + 内部 `min-width:0`；分类节点的范围徽标/计数/锁图标加 `pointer-events: none`（原先点徽标不触发 `node-click`，整行点选失效）。
  - **结果**：`pnpm -C frontend test:e2e` = **27 passed**（`filter-rail` 3 + `task-flow` 16 + 视觉回归 8）；后端 `mvn test` = **14 passed**；`e2e/scenarios.md` E-08/E-10 转 🟢。
  - **视觉基线重生（8/8 全部重写）**：左栏新增内容必须重生基线；本轮发现两处「门禁假绿」并修正——① `settle()` 未等左栏**异步**数据（`/categories`、`/menus`）落地，截图与 MSW 响应竞态，导致 8 张基线中 7 张**不含**分类树却仍通过对比；② Playwright 默认逐像素 `threshold=0.2` 会吸收浅色文本差异。修正：`settle()` 增等 `.cat-node` × 7 / `.bb-menu__item` × 3；删除旧 PNG 后 `--update-snapshots` 强制重写 8 张（`git status` 8/8 变更）。`playwright.config.ts` 与 `frontend/README.md` 已注记该门禁灵敏度与重生要求。
- **2026-09-29（新前端验收落地：MSW 直连 fixtures + E2E/视觉回归基线）**：
  - **fixtures 直连 MSW**：新增 `frontend/src/mocks/fixtures.ts`（`import.meta.glob` 注册 `docs/frontend-baseline/fixtures/api/**`），`frontend/src/mocks/handlers.ts` 改为**消费 fixtures**（读态原样返回、错误分支复用 `_errors/`、写态在内存副本变更），消除内联 mock 漂移；`vite.config.ts` 增 `server.fs.allow` 放行仓根 fixtures。
  - **E2E（后半链路）**：新增 `frontend/e2e/`（Playwright Test）+ `frontend/playwright.config.ts`（1440×900、Asia/Shanghai、`page.clock` 冻结 `2026-09-28T10:00+08:00`；本地复用系统 Edge，CI 用随包 Chromium）。覆盖 E-01/01b/02/03/04/05/06/07/09/11/12/13/14/15/16/17（E-08/E-10 因目标态 UI 未提供而留空）。
  - **视觉回归基线**：`frontend/e2e/visual.spec.ts` 8 张（登录/我的一天/全部任务/详情抽屉/编辑器/日历/用户管理/审计），基线落 `frontend/e2e/__screenshots__/` 并**入库**；`pnpm -C frontend test:e2e:update` 更新。
  - **结果**：`pnpm -C frontend test:e2e` = **24 passed**（16 场景 + 8 视觉）；CI 增 `e2e` 作业。
  - `e2e/scenarios.md` 状态列已回填「新验收」，`fixtures/README §5` 更新为「已直连」。
- **2026-09-29（0308 产品决策 · 保留 `BbCategoryTree`）**：产品确认 `BbCategoryTree`（`layoutNew/components/LeftBox/categoryTree.vue` → 分类树）**保留、必做**——分类是系统核心功能（`05` R5 分类与检索），不因旧实现「未 `import`/未挂载」而死代码化。它是分类树的**指定承接组件**，技术底座为原子组件 `BbTree`；契约（增/改名/删/拖拽 + 选中过滤任务、删除二次确认、`/categories` 接口落库）按目标态重写。与 `BbOrgTree`（组织机制树，0307 已废弃）**处置相互独立**，本次不改变契约计数（`BbCategoryTree` 仍在 15 业务契约内）。
- **2026-09-29（0307 产品决策 · 废弃 BbOrgTree）**：产品决定**放弃** `BbOrgTree`（`layoutNew/components/LeftBox/OrganizationalMechanismTree.vue`，旧实现「组件内静态假数据 + `radio-type` 被注释导致默认不渲染」的死组件，0303/N4 悬置项就此关闭）。动作：删除契约 `contracts/BbOrgTree.md`；业务契约 **16 → 15**、合计 **30 → 29**（冻结期 30/30 为历史事实，见下）；清理左栏/映射/索引及 `departments` fixture 的 `guides` 注释中对 `BbOrgTree` 的引用（`Departments` 仍供人员选择/部门筛选使用）。`BbCategoryTree` 的处置见其上 0308 条。
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
- **2026-09-29（0303 审核修订 · 综合复核层）**：真实登录抓包复核（legacy 形态 100% 吻合）。
  - **N1 闭环**（E2E 基线未跑）：新增 `scripts/golden-capture/e2e-baseline.mjs`，对旧前端跑通 **9 项**（E-01/03/04/06/09/11/12/16/17），`e2e/scenarios.md` 状态列已回填；其余 8 项标注 SKIP 原因（旧系统不可测/选择器不稳定/组件不可达）。
  - **N2 闭环**（覆盖率缺口）：补 `fixtures/api/departments/GET.tree.json`、`users/GET.me.json`；`BbOrgTree` 数据源改为 `GET /departments`，不再复用 users 树。
  - **N3**（合成数据）：`legacy-api/README.md` 增「合成 vs 真实」说明（结构真、数值合成，非缺陷）。
  - **N4**（BbOrgTree 不可达）：契约标注**去留待确认**（不阻断冻结）；重写须提供可用视图切换入口。**→ 已于 0307 产品决策废弃（见 §5 首条），本项关闭。**
  - **N5**（写态计数）：`fixtures/README` 由「24」更正为 **21**（实测）。
- **2026-09-29（0304 审核修订 · 四审层级）**：真实登录抓包复核（legacy 形态 100% 吻合）。
  - **D1 闭环**（映射表方法动词错误）：`contracts/README` 8 处 `POST→GET` 校正（`record.js` 实为 `method:'get'`：del/updateStatus/complete/completewithdraw/doCollect/delCollect/subrecord del|complete）；并补全 `/sys/category/*`、`/sysTag/*`、`/admin/user/*`、`/sys/file/*` 映射（D6）。
  - **D2 闭环**（日历路径）：`BbCalendarCard.md` 旧接口 `/calendar` → `/task/record/getproxycalendar`。
  - **D3 澄清**（视觉证据）：非「缺口」——证据已采集，**按用户指令不入库**（`.gitignore`），由 harness 复现；各契约尾注已由「待补充」改为「已采集（不入库）」。
  - **D4 闭环**（权限口径）：`BbTaskCard`/`BbTaskDetailDrawer`/`BbParticipantList` 三份增「权限口径收敛」注（基线=仅 owner；目标=RF3 参与者可写）。
  - **D5 闭环**（死组件）：`BbCategoryTree`/`BbOrgTree` 显式标注 **as-is 不可用**（`CategoryTree` 未 import、`OrgTree` `radio-group` 被注释）。**后续处置：`BbOrgTree` 0307 废弃；`BbCategoryTree` 0308 确认保留（必做）。**
  - **G1 闭环**（身份错位，P0）：`users/GET.me.json` 当前用户 `id=1`（admin）；`users/GET.index.json` 重排（admin=1、李四=12）；`tasks/*` 的 `owner/ccUsers` 名称对齐；两形态当前用户/归属人统一为 `1`。
  - **G2 澄清**（信封）：`api/` 为目标态（`code==0`，`02 §1.3` 设计决策）、`legacy-api/` 为现状态，**刻意不兼容**，非缺陷；已在 openapi `info.description` 与 `fixtures/README §1.1` 显式标注。
  - **G3 闭环**：补 `auth/POST.logout.json`、`POST.refresh.json`、`GET.external-config.json`。
  - **G4 闭环**：openapi 补 `Department`/`OperateLogVO`/`LoginLogVO`/`OperateLogPage`/`LoginLogPage`，并为 `/users/me`、`/departments`、`/tags`、`/categories`、`/menus`、`/audit/*` 定义 data schema。
  - **G5 闭环**：删除孤儿 `fixtures/api/users/GET.tree.json`。
  - **P2 闭环**（错误态盲区）：`_errors/` 9 → **19**（`02 §1.4` 非 0 码全覆盖）。
  - **§5 闭环**：本 README §2 #2「同一份 fixtures」措辞改为「同一逻辑数据集（两形态）」；openapi 标注目标态。
  - **§2/S1-S5**：`openapi` 为**目标态**，旧系统 S1（`/myself` 回传 password 哈希）/S2（`/wechat/getWeChat` 泄漏 `corpSecret`）/S3（文件下载无越权校验）/S4（GET 写操作）/S5（计数键名）均由目标设计（`02`）修正；**S2 需运维侧轮换线上密钥（已提请用户）**。

## 6. 关联文档

- 前端设计：`../../Task/03前端模块详细设计.md`
- 旧组件清单/映射：`../../Task/03前端模块详细设计.md §1.1`、`§2.9`
- API 契约源：`../../docs/api/openapi.yaml`
- 采集脚本（harness）：`../../scripts/golden-capture/README.md`

> **路径说明（0304/§5）**：以上相对路径均相对**本文件目录** `docs/frontend-baseline/`，解析到**仓库根 `bluebird-task/`** 下的 `Task/`、`docs/api/`、`scripts/`（`Task/` 为仓库内权威副本，`../../Task/` 已 `Test-Path` 校验通过）。注意目标仓库根即 `bluebird-task/`（非其父目录）。
