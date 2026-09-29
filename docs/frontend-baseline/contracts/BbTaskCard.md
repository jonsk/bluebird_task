# 行为契约：TaskCard.vue → BbTaskCard

> 源：`bluebird_task_Front/src/views/todolistModule/components/TaskCard.vue`（基于源码 + 03 §2.3.1 导出）；行号为导出时快照，以方法名/类名/字段名为准（见 contracts/README 行号约定）

| 项 | 契约内容 |
|---|---|
| **用途** | 列表中的单个任务项（任务卡） |
| **输入/状态来源** | `props.task`（旧 `task.item`，新类型 `TaskVO`：含 `owner:{id,name}`（映射旧 `belongUserId`）、`assigneeIds`、`ccIds`、`participantIds`）；`inject("indexPageObj")`；`taskStore` / `userStore`。`childTaskList.vue` 以 `:task="item"` 逐条传入（源码 `childTaskList.vue` L14-15） |
| **展示规则** | 创建时间「M月D日」；子任务 `completedTotal/total`；日期文本；重复文本；提醒文本；标签；附件图标；`@人员` 列表（均超长省略 + tooltip）；完成态标题加删除线（**收藏视图除外**）；子任务卡背景 `#f5f7fa`（`childTaskList.vue` L39，`tast-item` 在此场景复用深色底） |
| **着色规则** | 日期：`diffDays<=0` → `task-date-overdue`（红）；`diffDays<5` → `task-date-near`（绿）；否则默认。标签：含「紧急」红、含「重要」蓝、否则默认 |
| **交互清单** | ① 勾选完成 → `changeIsFinished`/`changeIncomplete` → 写 `/complete`/`/uncomplete`，**失败回滚 checkbox 状态**；② 子任务折叠 toggle；③ 标题点击 → 打开右侧详情抽屉；④ 收藏星标切换；⑤「移动任务」弹窗（选自定义栏 → `addTaskByCustom`）；⑥「删除任务」二次确认 → 删除并关闭右栏 |
| **业务规则（权限，修订 RF3）** | **参与者可写**：当前用户 ∈ {owner, assignee, cc}（或 ADMIN）时显示「加子任务」入口、可勾选完成/操作；**非参与者只读**。判定用 `task.owner.id`（映射旧 `belongUserId`）+ `task.participantIds`，**不单看 owner** |
| **边界与已知缺陷** | `onMounted` 用 `document.querySelector('.tast-item')` 量宽（脆弱，重写改用 CSS/ResizeObserver，见 R16）；`changeBtnFlag` 防抖 |
| **验收用例** | 1) 完成失败后勾选自动回滚；2) 逾期日期显示红色；3) 非参与者不显示加子任务/可否决完成；4) 子任务折叠可切换；5) 收藏视图完成态不加删除线 |

> 证据：`childTaskList.vue`、`taskListOne/Two.vue` 均复用本卡；`TaskVO` 契约见 `02 §4.5`。截图/录屏证据已采集（见 `../screenshots/README.md`；按策略**不入库**，由 `../../scripts/golden-capture/` 复现）。

> **权限口径收敛（0304/D4）**：本契约「业务规则（修订 RF3）」为**目标态（新系统）**=当前用户 ∈ {owner, assignee, cc} 或 ADMIN 可写；**基线（旧实现）**=仅 `belongUserId`（owner）可写、参与者只读（见 `BbTaskDetailDrawer.md`/`BbParticipantList.md` 的 `taskDisabled`）。重写以实现**目标态**为准；本条使三份契约口径一致。
