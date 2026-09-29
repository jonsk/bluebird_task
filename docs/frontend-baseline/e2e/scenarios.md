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

## 场景清单

| ID | 场景 | 关键步骤（旧前端基线） | 新前端验收要点 | 状态 |
|---|---|---|---|---|
| E-01 | 登录（账密） | 输入账密→提交→进入首页 | 走 `/auth/login`；错误提示；LOCAL 默认无外部入口 | 🟢 基线通过(旧) → `/#/index`；捕获 `POST /api/user/login` |
| E-02 | 登录（外部-可选） | 企微扫码→回调；OIDC 点击→整页跳 `authorization/oidc`→回调 | OIDC 按钮跳 `/oauth2/authorization/oidc`；`OidcSuccessView` 收 hash token 后清 fragment（RF2/R9） | ◻ 旧系统仅 LOCAL，外部登录不可测 |
| E-03 | 六大视图切换与计数 | `/index,/myWeek,/myJoin,/myDo,/myCollect,/allTask` 各点一次 | 路由↔scope 映射表正确（03 §4.3）；`/tasks/count` 各视图计数一致 | 🟢 基线通过(旧) → 卡片数 7/8/1/1/1/10，与断言一致 |
| E-04 | 新建主任务 | 输入标题→配日期/提醒/重复/分配→添加 | POST `/tasks`；due_at/remind/cycle_rule/assigneeIds 正确；成功 emit 刷新；失败提示不丢输入（见 BbTaskComposer 缺陷） | 🟢 基线通过(旧) → 捕获 `POST /task/record/add` |
| E-05 | 新建子任务 | 详情内子模式添加 | `parent_id` 正确；`:key=id` 不串位（见 BbSubtaskList） | ◻ 旧前端子模式选择器不稳定，转手工/新前端验收 |
| E-06 | 完成任务与失败回滚 | 勾选完成→接口失败→勾选回滚 | `/tasks/{id}/complete`；失败回滚 checkbox；recurring 失败不置 completed | 🟢 基线通过(旧) → 捕获 `complete`；失败后勾选数不增（回滚生效） |
| E-07 | 收藏/取消 | 星标切换 | `/tasks/{id}/collect` + DELETE；收藏视图读取 | ◻ 星标触发元素无稳定选择器，转手工 |
| E-08 | 移动任务到自定义栏 | 选栏→确认 | `POST /menus/{id}/items`；跨视图刷新 | ◻ dropdown 定位链路，转手工/新前端验收 |
| E-09 | 删除任务（二次确认） | 删除→确认 | DELETE；二次确认文案「清空关联数据、不可恢复」（R5） | 🟢 基线通过(旧) → 确认框文案含「删除」；捕获 `/task/record/del` |
| E-10 | 分类树增删改 | 左栏分类树操作 | `/categories` CRUD；树刷新 | ◻ 旧 `categoryTree` 未挂载/不可达（见 BbCategoryTree·N4） |
| E-11 | 标签增删改 | 标签配置 | `/tags` CRUD | 🟢 基线通过(旧) → 捕获 `POST /sysTag/add`（新增） |
| E-12 | 人员选择按部门筛选 | 选人弹窗→部门→搜索 | `/users?deptId=&keyword=`；手机号脱敏；优先本部门（02 §2.5/03） | 🟢 基线通过(旧) → 选人弹窗打开并捕获 `/admin/user/list` |
| E-13 | 附件上传/预览/删除 | 上传→出现→预览 | `/files` 上传 / `GET /files/{id}/preview`（登录取）；磁盘级联删 | ◻ 需文件输入，转手工 |
| E-14 | 用户/部门管理（ADMIN） | 建用户/建部门 | 权限 `hasRole(ADMIN)`；首登改密引导 | ◻ 旧前端管理页路由未确认，转手工 |
| E-15 | 审计日志查询（ADMIN） | 查操作/登录日志 | `/audit/operates`、`/audit/logins`；参数脱敏 | ◻ 旧前端审计页路由未确认，转手工 |
| E-16 | 周期任务 | fixtures 含周期任务：列表/日历按展开实例显示；完成推进下一实例 | recurring 豁免 `completed=0`；complete/dueAt 推进 `cycle_last_completed`（R2）；counts 按展开实例 | 🟢 基线通过(旧) → 卡片检出重复文案（每天/每周/…） |
| E-17 | 刷新 token / 未登录 | token 失效→refresh；未登录→登录页 | 20005→refresh；10002→登录；`/auth/refresh` 匿名白名单 | 🟢 基线通过(旧) → 未登录跳 `/#/login?redirect=/index` |

> 旧基线脚本（`e2e-baseline.mjs`）已在与黄金截图同套 fixtures 下跑通前半链路（旧基线）；SKIP 项将在新前端实现后由 `◻→🟢 验收通过(新)` 补齐（部分需人工或待组件可达性修复）。

## 周期任务 fixtures（M3）

fixtures 必须含「周期任务」样例（`03 §2.3.3`/独立审核 M3 明确为范围内），涵盖：
- 无限循环（`count`/`until` 皆空）
- `count` 终止 / `until` 终止
- WEEKLY+`byDay`（如 `["MO","WE","FR"]`）、DAILY、MONTHLY
- `cycle_last_completed` 已推进 / 未开始
- 跨时区（`tz: Asia/Shanghai`）

> 逐条场景的 Playwright 脚本已落地为 `scripts/golden-capture/e2e-baseline.mjs`（旧基线 9 项通过，2026-09-29）；截图证据见 `../screenshots/`。
