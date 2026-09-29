# 行为契约：taskListOne.vue / taskListTwo.vue → BbTaskList

> 源：`bluebird_task_Front/src/views/todolistModule/components/taskListOne.vue`、`taskListTwo.vue`（已读源码；行号为导出时快照，以方法名/类名/字段名为准）

- 用途：任务列表容器。`taskListOne` 为「未完成 + 已完成」双栏布局（已完成折叠面板）；`taskListTwo` 为单一列表展示全部任务。二者均不含任务项逻辑，仅 `v-for` 渲染 `TaskCard`。
- 输入/状态来源：
  - `props`：无（两个组件都不定义 props）。
  - `inject`：无。
  - `store`：Pinia `taskUseStore`（`@/store/modules/task`，`persist: true`）。
    - `taskListOne`：`taskStore.task_list`、`taskStore.task_list_finished`（均为 `computed` 只读映射）。
    - `taskListTwo`：仅 `taskStore.task_list`。
  - 全局：无。（`selectDateValue`、`activeDate` 为局部 `ref`/常量，未被使用，属死代码。）
- 展示规则：
  - `taskListOne`：外框 `.t-b-list-box`（`overflow-auto`，高度 `calc(100%-100px)`）内先渲染 `task_list`（未完成）逐条 `TaskCard`；随后 `.ywc-box` 用 `el-collapse`（`accordion`）承载「已完成」，标题含固定文案「已完成」+ `task_list_finished.length` 计数，默认展开（`ywcActiveNames = ref(['1'])`，`el-collapse-item name="1"`），面板内逐条 `TaskCard`。
  - `taskListTwo`：外框内直接渲染 `task_list` 逐条 `TaskCard`。
  - 列表项：`<TaskCard :key="item.id" :task="item" />`；外层包裹 `div` 的 `:key` 使用数组 **下标 `index`**（非 `item.id`）。
- 交互清单：本组件自身**无交互**，全部交互（勾选完成、收藏、打开详情、删除、子任务等）委托给 `TaskCard`：
  - 点击任务标题 → 打开右侧详情抽屉（`taskStore.openTaskRightBox`）→ 无接口 → 无回滚（详见 BbTaskCard 契约）。
  - 勾选完成 → `taskStore.changeIsFinished` / `changeIncomplete` → 旧 `GET /task/record/complete|completewithdraw`，新 `POST /tasks/{id}/complete` / `POST /tasks/{id}/uncomplete`。
  - 删除 / 收藏 / 移动任务 → 见 `BbTaskCard.md`。
  - `taskListOne` 折叠标题点击 → 展开/收起「已完成」面板 → 无接口。
- 业务规则：
  - 数据格式化**不在本组件**：`formatListHander`、`convertBasedOnStatus`、`formatServerObj` 均在 `store/modules/task.js` 的 `getTskListByType` 内完成（`task.js` L175-276）。契约线索中的这两个方法名归属 store，不属本列表组件。
  - 未完成 / 已完成双查：非「全部任务」视图下，store 并发请求 `completeStatus:"0"` 与 `"1"`，分别写入 `task_list` 与 `task_list_finished`。
  - 全量视图（`TASK_TYPE_LINK = "allTask"`）：只请求一次（`completeStatus: null`）并仅写入 `task_list`，**`task_list_finished` 不更新**；此时 `taskListOne` 的「已完成」折叠面板计数保持旧值（列表视图与选择器不匹配的隐患）。
  - 每页 `size: 9999`、`current: 1`，无分页/无限滚动（`v-infinite-scroll`、`loadPage`、`VueDraggable` 均已注释）。
- 边界与已知缺陷：
  - 外层 `v-for` 的 `:key="index"` 与内部 `TaskCard :key="item.id"` 不一致，列表增删时可能触发复用/重渲染异常（重写应用稳定 id）。
  - 无 `loading` / `empty` / 错误态渲染，请求在 store 内 `.catch` 仅 `console.log`，界面静默。
  - 全量视图下「已完成」栏与 `task_list_finished` 脱节（如上）。
  - `selectDateValue`、`activeDate`、`childerTaskList` 式死代码；样式中 `.infinite-list` 为遗留无效样式。
  - 两个列表组件选择由父级（路由/`indexPage`）决定，**待确认**：具体在何组件按视图切换 `taskListOne` / `taskListTwo`。
- 验收用例：
  1. 进入「我的一天」等常驻视图 → 未完成区渲染 `task_list`、已完成折叠面板渲染 `task_list_finished` 且计数等于数组长度。
  2. 默认状态「已完成」面板为展开（`ywcActiveNames=['1']`）。
  3. 切换到「全部任务」→ 仅单一列表展示全部任务，不出现/不刷新已完成栏。
  4. `taskListTwo` 场景下所有任务（含已完成）在同一列表按后端顺序渲染。
  5. 列表项点击标题可打开对应任务详情抽屉，传入 `task` 与列表项一致。
  6. 数据为空时不渲染任何 `TaskCard`（无占位，作为旧行为基线）。
