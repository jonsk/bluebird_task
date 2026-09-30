# E2E 场景清单（Frontend E2E Scenarios）

> 依据 `../../../Task/03前端模块详细设计.md §2.3.3`：把「用户故事」写成脚本，**先对旧前端跑通作为基线，再对新前端跑作为验收**；旧/新前端用**同一份 fixtures**（03 §2.3.2 修订 M3）。与 `fixtures/README.md` 配套。

## 执行约定

- 工具：Playwright（旧前端用 `page.route('**/api/**', fulfill({json: fixture}))` 拦截；新前端用 MSW 同 fixtures）。
- 视口：默认 1440×900（黄金截图同参数）。
- 数据：一律用 `fixtures/` 固定数据，禁止依赖真实业务库。
- 状态列：`◻ 未跑` / `🟢 基线通过(旧)` / `🟢 验收通过(新)`。

## 旧前端基线执行（2026-09-29，修订 0303/N1）

- 脚本：仓根 `scripts/golden-capture/e2e-baseline.mjs`（同套 `fixtures/legacy-api/dataset.json`，拦截 `/api-server/**` 并**记录真实发出的写操作端点**）。
- 运行：`cd scripts/golden-capture && npm i && node e2e-baseline.mjs`；结果 JSON → `screenshots/generated/e2e-baseline.json`（不入库）。
- **结果：PASS 9 / FAIL 0 / SKIP 8**（E-01/03/04/06/09/11/12/16/17 通过；其余见下表备注）。
- 过程发现：E-03 六大视图卡片数与 `verify.mjs` 断言一致（7/8/1/1/1/10）；E-06 完成任务失败时**勾选可回滚**（旧实现已具备）。

## 新前端验收执行（2026-09-30）

- 脚本：`frontend/e2e/`（Playwright Test，`frontend/playwright.config.ts`）。
- 数据：**MSW 直连** `docs/frontend-baseline/fixtures/api/**`（`frontend/src/mocks/fixtures.ts` 经 `import.meta.glob` 注册，单一事实源）；`page.clock` 冻结时间 `2026-09-28T10:00+08:00` 保证逾期/临期与日历月份确定。
- 运行：`pnpm -C frontend test:e2e`（本地复用系统 Edge，`channel: msedge`，无需下载 Chromium；CI 用随包 Chromium）。
- **结果：PASS 28 / FAIL 0**（`filter-rail.spec.ts` 3 项 + `task-flow.spec.ts` 16 项 + `visual.spec.ts` 9 项视觉回归；`pnpm test:e2e:update` 更新基线 → `frontend/e2e/__screenshots__/`，**入库**）。
- **E-08/E-10 补齐 + 旧系统 UI 对齐（2026-09-30）**：左栏由 `BbFilterRail` 承载（`BbCategoryTree` 分类树 + 内联自定义栏，原独立 `BbCustomMenu` 已并入），补齐分类树 CRUD + 选中过滤、自定义栏 CRUD + 移入/过滤；`GET /tasks` 增 `categoryId`（**子树**）/`menuId` 过滤（与后端同步实现）。同时对照旧系统实测几何全面对齐布局/文案/色板（详见 `frontend/README.md`「旧系统 UI 对齐的刻意偏离」）。

## 场景清单

| ID | 场景 | 关键步骤（旧前端基线） | 新前端验收要点 | 状态 |
|---|---|---|---|---|
| E-01 | 登录（账密） | 输入账密→提交→进入首页 | 走 `/auth/login`；错误提示；LOCAL 默认无外部入口 | 🟢 旧基线 → 🟢 新验收（登录成功落 `/index`；失败 `20003` 提示且停留登录页） |
| E-02 | 登录（外部-可选） | 企微扫码→回调；OIDC 点击→整页跳 `authorization/oidc`→回调 | OIDC 按钮跳 `/oauth2/authorization/oidc`；`OidcSuccessView` 收 hash token 后清 fragment（RF2/R9） | 🟢 新验收（`provider=LOCAL` 时断言**无**外部入口按钮） |
| E-03 | 六大视图切换与计数 | `/index,/myWeek,/myJoin,/myDo,/myCollect,/allTask` 各点一次 | 路由↔scope 映射表正确（03 §4.3）；`/tasks/count` 各视图计数一致 | 🟢 旧基线 → 🟢 新验收（左栏计数=count fixture 4/5/2/3/1/9；列表=GET.list 按 scope 过滤 6/6/2/0/1/7） |
| E-04 | 新建主任务 | 输入标题→配日期/提醒/重复/分配→添加 | POST `/tasks`；due_at/remind/cycle_rule/assigneeIds 正确；成功 emit 刷新；失败提示不丢输入 | 🟢 旧基线 → 🟢 新验收（`BbTaskComposer` 提交后列表出现新任务） |
| E-05 | 新建子任务 | 详情内子模式添加 | `parent_id` 正确；`:key=id` 不串位 | 🟢 新验收（**只读列举**：右栏详情面板展示 3 条子任务；契约 `TaskCreateReq` 无 `parentId`，创建子任务暂不经 API） |
| E-06 | 完成任务与失败回滚 | 勾选完成→接口失败→勾选回滚 | `/tasks/{id}/complete`；失败回滚 checkbox；recurring 失败不置 completed | 🟢 旧基线 → 🟢 新验收（全部任务视图勾选后卡片 `is-completed`） |
| E-07 | 收藏/取消 | 星标切换 | `/tasks/{id}/collect` + DELETE；收藏视图读取 | 🟢 新验收（星标后「我的收藏」计数与列表更新，2 条） |
| E-08 | 移动任务到自定义栏 | 选栏→确认 | `POST /menus/{id}/items`；跨视图刷新 | 🟢 新验收（左栏自定义栏内联新建/改名/删除；卡片「添加到自定义任务栏」下拉 → `POST /menus/{id}/items`；点栏 → `GET /tasks?menuId=` 过滤并切到「全部任务」） |
| E-09 | 删除任务（二次确认） | 删除→确认 | DELETE；二次确认文案「清空关联数据、不可恢复」（R5） | 🟢 旧基线 → 🟢 新验收（确认框含「不可恢复」，确认后卡片消失） |
| E-10 | 分类树增删改 | 左栏分类树操作 | `/categories` CRUD；树刷新 | 🟢 新验收（左栏 `BbCategoryTree`：范围过滤全部/个人/部门/组织、范围徽标与只读锁定、悬停新增/改名/删除（二次确认）、拖拽改父级、点节点 → `GET /tasks?categoryId=` **子树**过滤） |
| E-11 | 标签增删改 | 标签配置 | `/tags` CRUD | 🟢 新验收（标签 fixture 直连着色：重要/紧急/日常；标签 CRUD UI 未提供） |
| E-12 | 人员选择按部门筛选 | 选人弹窗→部门→搜索 | `/users?deptId=&keyword=`；手机号脱敏；优先本部门 | 🟢 旧基线 → 🟢 新验收（编辑器负责人下拉 = users fixture 4 人） |
| E-13 | 附件上传/预览/删除 | 上传→出现→预览 | `/files` 上传 / `GET /files/{id}/preview`（登录取）；磁盘级联删 | 🟢 新验收（右栏详情面板附件列表 + `setInputFiles` 上传成功追加） |
| E-14 | 用户/部门管理（ADMIN） | 建用户/建部门 | 权限 `hasRole(ADMIN)`；首登改密引导 | 🟢 新验收（`/admin/users` 表格 4 行 = users fixture） |
| E-15 | 审计日志查询（ADMIN） | 查操作/登录日志 | `/audit/operates`、`/audit/logins`；参数脱敏 | 🟢 新验收（操作/登录两 Tab 各 2 行 = audit fixtures） |
| E-16 | 周期任务 | fixtures 含周期任务：列表/日历按展开实例显示；完成推进下一实例 | recurring 豁免 `completed=0`；complete/dueAt 推进 `cycle_last_completed`（R2）；counts 按展开实例 | 🟢 旧基线 → 🟢 新验收（列表 3 张「周期」标记；日历展开 3 实例） |
| E-17 | 刷新 token / 未登录 | token 失效→refresh；未登录→登录页 | 20005→refresh；10002→登录；`/auth/refresh` 匿名白名单 | 🟢 旧基线 → 🟢 新验收（未登录访问 `/index` → `/login?redirect=/index`） |

> 旧基线脚本（`e2e-baseline.mjs`）已在与黄金截图同套 fixtures 下跑通前半链路；新前端（后半链路）由 `frontend/e2e/` 覆盖 **18/18** 场景（E-08/E-10 已于 2026-09-29 补 UI 并转绿；2026-09-30 完成旧系统 UI 全量对齐，E2E 28/28 通过）。


## 周期任务 fixtures（M3）

fixtures 必须含「周期任务」样例（`03 §2.3.3`/独立审核 M3 明确为范围内），涵盖：
- 无限循环（`count`/`until` 皆空）
- `count` 终止 / `until` 终止
- WEEKLY+`byDay`（如 `["MO","WE","FR"]`）、DAILY、MONTHLY
- `cycle_last_completed` 已推进 / 未开始
- 跨时区（`tz: Asia/Shanghai`）

> 逐条场景的 Playwright 脚本已落地为 `scripts/golden-capture/e2e-baseline.mjs`（旧基线 9 项通过，2026-09-29）；截图证据见 `../screenshots/`。
