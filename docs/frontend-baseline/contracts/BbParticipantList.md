# 行为契约：参与人展示（TaskCard `@` 列表 + RightBoxDialog 用户区 + selectUser 选择器）→ BbParticipantList

> 源：`bluebird_task_Front/src/views/todolistModule/components/TaskCard.vue` 的 `@` 人员片段（L120-129）；`bluebird_task_Front/src/layoutNew/components/RightBoxDialog/index.vue` 的用户展示/选择区（模板 L297-323，脚本 `confrimSelectUser` L692-705、`deleteSelectedUsers` L757-763）+ 选人弹窗 `bluebird_task_Front/src/views/todolistModule/components/selectUser.vue`（已读 L1-266）。行号为导出时快照，以方法名/类名/字段名为准。
> 说明：全仓 `glob`（`layoutNew/components/**/*.vue`、`todolistModule/**/*.vue`）**未发现独立的参与人展示/头像组件**；参与人信息仅有「卡片内 `@` 文本」与「详情抽屉用户行」两处展示，以及 `selectUser.vue` 弹窗负责选择。旧前端**全程以逗号拼接的用户名文本展示，没有任何头像（Avatar）渲染**。

- 用途：展示任务参与人（接收用户），并在详情抽屉中提供打开选人弹窗、回显已选、清空参与人的能力。卡片侧为只读文本。
- 输入/状态来源：
  - `props`：`TaskCard` 的 `props.task`；参与人字段为 `task.userNameList`（展示用名字串）与 `task.userIdList`（选择器回显用的 id 串，卡片侧未用）。
  - `inject`：无。
  - `store`：`taskStore.dialog_right_config_Obj`（详情抽屉编辑对象，读写 `userIdList`、`userNameList`）；`userStore.id`（配合 `taskDisabled` 判权限）。
  - 子组件：`selectUser`（`RightBoxDialog` 内 `ref="selectUserRef"`，L358-359），以 `show(userIdList)` 打开、`@confrim="confrimSelectUser"` 回传。
  - 全局：`getCurrentInstance().proxy`（`$modal`）；`selectUser` 内 `unref` 为自动导入。
- 展示规则：
  - 卡片（`TaskCard.vue` L120-129）：`v-if="task.userNameList"` 时渲染「前置圆点 + 字面 `@` + `task.userNameList`」；文本套 `.span-w.lone-w`（`overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:150px`）单行省略，`el-tooltip :content="task.userNameList"` 悬浮显示全文。**无头像、无逐个用户拆分、无角色区分**。
  - 详情抽屉用户行（`RightBoxDialog` L297-323）：`@` 文本图标（`el-icon`）+ 文本 `taskStore.dialog_right_config_Obj.userNameList || '选择用户'`（`.singe-line.w-250`，单行省略，宽 250px）+ 全文 `el-tooltip`；行尾 `Delete` 图标 `v-show="userIdList && !taskDisabled"`（tooltip 文案误写为「删除提醒」，L312-315）。整行 `@click="selectUserRef.show(dialog_right_config_Obj.userIdList || '')"`。
  - 选人弹窗（`selectUser.vue`）：`el-dialog`「选择接收用户」宽 1024px；用户名列 `prop="name"`（悬浮 `el-popover` 显示 `username`）+ 所属部门列；表格多选 + 分页；「本部门用户/全部用户」开关。选择结果在 `confrimSelectUser` 中拆分为 `ids`/`names`。
  - 只读态：`taskDisabled = completeStatus==='1' || userStore.id !== belongUserId` 时不显示删除参与人入口、整行不可选人（详情抽屉 `.drbb-main-disabled` 遮罩，见 BbTaskDetailDrawer）。
- 交互清单：
  - 卡片 `@` 文本 → 仅 tooltip 展示，**无交互**（点击冒泡到标题容器打开详情抽屉）。
  - 详情用户行点击 → `selectUserRef.show(userIdList || '')` → 打开选人弹窗、加载用户列表 → 无任务接口调用。
  - 选人弹窗勾选/搜索/翻页/切换本部门 → `selectUser.vue` 内部 `listUser`/`getIdsByUser`（`GET /admin/user/list`、`GET /admin/user/getListByIds`）；点击「确定」→ `emit("confrim", userItems)`（**事件名拼写为 `confrim`**）。
  - `confrimSelectUser(list)`（L692-705）：遍历回传对象，`ids.push(id)`、`names.push(name)` → 写入 `dialog_right_config_Obj.userIdList = ids.join(",")`、`userNameList = names.join(",")` → `updateRightItemHandler({type:'userIdList'})` → `taskStore.updateTask` → 旧 `POST /task/record/update`，新 `PUT /tasks/{id}`；成功后刷新 `bfRightItem` 并 `getTaskTypeLen()`，失败仅 `console.log`（无回滚）。
  - 删除参与人 `deleteSelectedUsers`（L757-763）：将 `userIdList`/`userNameList` 置空字符串 → `updateRightItemHandler({type:'userIdList'})` 提交；无二次确认。
- 业务规则：
  - 参与人在旧模型中以 `userIdList`（逗号拼接 id）与 `userNameList`（逗号拼接 name）两个**字符串**字段存储与展示，前端通过 `split`/`join` 维护（本组件只 `join`，拆分发生在选人结果回传时）。
  - 选择器回显：`show(userIdList)` 将逗号串作为 `getIdsByUser({userids: ids})` 参数上报（`selectUser.vue` L185-189），期望数组还是逗号串 **待确认**（以接口文档/后端为准）。
  - 新契约字段：`TaskVO` 用 `participantIds[]`（并含 `owner`/`assignees[]`/`ccUsers[]`，见 `fixtures/api/tasks/GET.detail.json`）；旧 `userIdList`/`userNameList` 与新参与人结构（`{id,name}` 数组）的映射 **待确认**，重写时应改为对象数组而非拼接串。
  - 去重仅发生在选人弹窗内部（`selectUser.handleSelectionChange` 以 `id` 去重，且**只做并集追加、取消勾选不移除**）；展示层不做去重/排序。
  - 权限沿用详情抽屉 `taskDisabled`：已完成或非归属人只读，但仍以 `userStore.id !== belongUserId` 严格相等判定（类型不一致会误判）。
- 边界与已知缺陷：
  - **旧前端无头像**：卡片与详情均仅渲染 `@` + 名字文本；若新 `BbParticipantList` 要求头像/首字母徽标（Avatar）列表，**待确认：旧源无对应实现，需依 03 设计稿新建**。
  - `userNameList` 与 `userIdList` 靠下标隐式对应，任一侧增删不一致会导致「名字与 id 错位」。
  - 参与人名字串为整体省略（`lone-w max-width:150px`），**多参与人只显示一行且无法区分个数/角色**。
  - 详情行删除图标 tooltip 文案误写为「删除提醒」（应为「删除参与人」）（L312）。
  - 删除参与人无二次确认且失败不回滚（`updateRightItemHandler` 失败静默）。
  - `selectUser` 取消勾选不移除已选（`handleSelectionChange` 仅并集），会在确定时回传已取消用户（见 `BbUserSelect.md`）。
  - 权限判定只看归属人 `belongUserId`，未纳入 `participantIds`/assignee/cc；按 `BbTaskCard.md` 修订 RF3，参与者应可写而当前会被判只读。
  - `selectUser.vue` 无 `.catch`，`getIdsByUserFun` 仅成功分支赋值；`resetQuery` 未同步 `switchValue`。
- 验收用例：
  1. 任务的 `userNameList` 非空 → 卡片显示 `@` 与该名字串（超长省略 + tooltip 全文）；为空 → 不渲染该片段。
  2. 详情抽屉用户行无参与人时显示占位「选择用户」；有参与人时显示 `userNameList` 且悬浮 tooltip 为全文。
  3. 点击详情用户行打开「选择接收用户」弹窗；确定后 `userIdList`/`userNameList` 按所选 `id`/`name` 以逗号拼接并触发任务更新接口。
  4. 点击删除参与人入口 → 参与人被清空并调用更新接口；已完成/非归属人任务不显示该入口。
  5. 参与人 `userIdList` 非空时重新打开选人弹窗，已选用户在列表加载后应处于勾选态（旧实现存在回显竞态，重写后须通过）。
  6. 多参与人时展示为一行省略文本，悬浮可查看全部用户名（新头像列表的逐人展示为待确认项）。

> **权限口径收敛（0304/D4）**：**基线（旧实现，本契约所记录）**=仅 `belongUserId`（owner）可写、参与者/抄送只读（`taskDisabled`）；**目标态（新系统，RF3）**=当前用户 ∈ {owner, assignee, cc} 或 ADMIN 可写。重写以实现**目标态**为准；本条使 `BbTaskCard`/`BbTaskDetailDrawer`/`BbParticipantList` 三份口径一致。
