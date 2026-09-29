# 黄金截图与录屏证据（Golden Screenshots / Evidence）

> 本目录登记基线冻结的**黄金截图**（Playwright 固定视口导出）与录屏证据。
> **策略（0304/D3，依用户指令）**：图片/录屏**不纳入版本库**（`.gitignore` 排除 `*.png|jpg|jpeg|webm|mp4` 与 `generated/`）；版本库内仅保留本说明 + `.gitkeep`，证据由 harness 确定性复现。
> 依据 `../../../Task/03前端模块详细设计.md §2.3.2`。

## 状态

- ✅ **已采集（2026-09-28）**：旧前端（线上隔离环境）跑通，固定 fixtures + 固定视口导出。**基线批 19 张截图 + 1 段录屏**（含六大视图、抽屉、及组件级下拉/对话框）。
- ✅ **空/异常态（2026-09-29）**：`capture-states.mjs` 追加 **19 张**（6 空态 + 6 `body.code=500` + 6 HTTP500 静默 + 1 登录失败）。**当前合计 38 张 PNG + 1 段 webm**（图片/录屏**不入库**，见 `.gitignore`；由 harness 复现）。
- 采集方式：仓根 `scripts/golden-capture/`（Playwright + msedge，拦截 `/api-server/**` 注入 `../fixtures/legacy-api/dataset.json`），**确定性可复现**。复采：`cd scripts/golden-capture && npm i && node capture.mjs`（另 `capture-components.mjs` / `capture-states.mjs`）。

## 证据清单

| 文件 | 视图 / 状态 |
|---|---|
| `login-account-1440x900.png` | 登录页 · 账号密码 tab |
| `tasklist-day-1440x900.png` | 我的一天（未完成 + 已完成） |
| `tasklist-week-1440x900.png` | 未来 7 天任务 |
| `tasklist-joined-1440x900.png` | 我@Ta 的任务 |
| `tasklist-assigned-1440x900.png` | 分配给我的任务 |
| `tasklist-collect-1440x900.png` | 我的收藏 |
| `tasklist-all-1440x900.png` | 全部任务 |
| `tasklist-day-complete-1440x900.png` | 我的一天 · 完成任务交互后 |
| `task-detail-drawer-1440x900.png` | 任务详情右侧抽屉（拣选任务卡片打开） |
| `recordings/tasklist-day-complete.webm` | 完成任务的交互录屏 |

### 组件级（`capture-components.mjs`）

| 文件 | 组件（旧 → 新） |
|---|---|
| `component-sidebar-leftbox-1440x900.png` | 左栏（视图导航 + 分类树 + 组织机制树）→ `BbCategoryTree`（`BbOrgTree` 已于 0307 废弃，本截图保留为历史证据） |
| `component-calendar-card-1440x900.png` | 日历卡片 `CalendarCard` → `BbCalendarCard` |
| `component-composer-1440x900.png` | 新增任务块（收起）`addTaskBlock` → `BbTaskComposer` |
| `component-composer-expanded-1440x900.png` | 新增任务块（聚焦展开配置区） |
| `popover-drawer-date-1440x900.png` | 截止日期下拉 `dropdownSetDate` → `BbDatePicker` |
| `popover-drawer-remind-1440x900.png` | 提醒下拉 `dropdownSetTips` → `BbRemindSelect` |
| `popover-drawer-repeat-1440x900.png` | 重复下拉 `dropdownSetEach` → `BbRepeatSelect` |
| `popover-drawer-tags-1440x900.png` | 详情内标签选择 → `BbTagConfig`（选择态） |
| `popover-tag-config-1440x900.png` | 顶栏标签配置下拉 `TagConfig` → `BbTagConfig` |
| `dialog-select-user-1440x900.png` | 人员选择对话框 `selectUser` → `BbUserSelect` |

### 空态 / 异常态（`capture-states.mjs`）

| 文件 | 状态 |
|---|---|
| `state-empty-{day,week,joined,assigned,collect,all}-1440x900.png` | 六大视图 · 列表为空 |
| `state-error-{day,week,joined,assigned,collect,all}-1440x900.png` | 六大视图 · 接口错误（`body.code=500` → `ElMessage` 错误提示） |
| `state-httperr-*.png` | 六大视图 · HTTP 层 500（**静默无提示**，见下缺陷） |
| `state-error-login-1440x900.png` | 登录失败（`ElMessage` 错误提示） |

> **既有缺陷留痕（供重写修正决策）**：
> 1. 旧前端 `utils/request.js` 对 **HTTP 非 2xx** 的错误分支**只 reject、不弹提示**（原 `ElMessage` 被注释），用户无反馈；仅当 HTTP 200 且 `body.code≠200` 才提示。→ 重写应统一错误 UI。
> 2. 错误态下 `ElMessage` 会**累积堆叠**（每视图 3 条：未完成/已完成/count），无节流去重。

截图覆盖任务卡片关键态（逾期红 / 临期绿 / 正常 / 已完成删除线 / 收藏高亮 / 子任务展开 / 标签着色 / @人 / 附件图标）。

## 采集约束与说明

1. **固定视口**：`1440×900`、`deviceScaleFactor:1`，禁用 CSS 过渡/动画（`addInitScript` 注入）。
2. **固定数据**：线上库**任务为空**，故用 `fixtures/legacy-api/dataset.json`（旧接口形态、合成数据）经 `page.route` 注入渲染；因此截图**不含真实业务数据、不含密钥/口令**。
3. **登录**：真实登录流程（`admin`）但登录响应亦被 fixture 覆盖；仅静态资源来自线上服务器。
4. **验证**：仓根 `scripts/golden-capture/verify.mjs` 断言各视图卡片数（day=7 / week=8 / joined=1 / assigned=1 / collect=1 / all=10，含子任务内联）。

## 与对照验收的关系（`03 §2.5.1`）

新前端同数据同视口截图 → 与黄金截图做**结构化 DOM 断言为主 + 像素 diff 为辅**（修订 G7）；差异需记录「有意改进」或修正。
新前端所需数据形态见 `../fixtures/api/`（新接口形态）；两套 fixture 的对应关系见 `../README.md §fixtures 双形态`。
