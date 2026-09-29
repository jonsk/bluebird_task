# 行为契约：RightBoxDialog/index.vue → BbTaskDetailDrawer

> 源：`bluebird_task_Front/src/layoutNew/components/RightBoxDialog/index.vue`（已读源码 L1-996；行号为导出时快照，以方法名/类名/字段名为准）

- **用途**：任务详情 / 编辑右侧抽屉。承载任务完成勾选、标题、步骤（子任务）、截止日期、提醒、重复、标签、附件、接收用户、备注，以及删除任务与关闭抽屉。
- **输入/状态来源**：
  - `props`：无。
  - `inject`：无。
  - `store`：`taskUseStore`（`dialog_right_config_Obj` 为被编辑对象、`task_right_box_show` 控制显隐由父级消费）；`useUserStore`（`userStore.id`）；`taskTagUseStore`（`tagTagStore.tagList` 全量标签）。
  - 子组件：`dropdownSetDate`（`v-model:dataText`）、`dropdownSetTips`、`dropdownSetEach`、`selectUser`（ref `selectUserRef`，`show(userIdList)` 打开、`@confrim` 回调）。
  - 全局：`getToken()`（上传 `Authorization` 头）、`import.meta.env.VITE_APP_BASE_API`、`permission.js` 的 `proxy.$modal`。
  - `watch(() => dialog_right_config_Obj.id, immediate:true)`：id 变化即调用 `getStepTaskFun()` 拉取步骤。
- **展示规则**：
  - 只读开关 `taskDisabled = computed(() => completeStatus === "1" || userStore.id !== belongUserId)`（**严格相等**）。为真时：主容器加 `.drbb-main-disabled`（各配置行显示遮罩 `:after`），完成框/标题/备注/上传/标签/用户选择等 `disabled`/`readonly`。
  - 主行：完成 `el-checkbox`（`true-value="1"`、`false-value="0"`）；标题 `el-input type=textarea autosize`，`completeStatus==='1'` 时加 `line-through`；右侧收藏星标（`isImportant` → `StarFilled` 蓝 `#2564cf`，否则 `Star`）。
  - 步骤行：来自 `setpTask`（`getStepTask` 返回）；`completeStatus==='1'` 时文本删除线 + `readonly`，且该行删除图标 `v-show` 隐藏。
  - 配置行：截止日期（`dateText`，空显示「添加截止日期」）、提醒（`tipsText`，空显示「提醒我」）、重复（`eachText`，空显示「重复」）；有值时行 `drbbi-item-acive`（蓝 `#2664cf`）并在尾侧显示删除图标（`dateText/tipsText/eachText` 有值 `&& !taskDisabled`）。标签 `el-select multiple` 占位「选择标签」；附件区「添加文件」；用户区显示 `userNameList`（空显示「选择用户」）与 `@` 图标；备注 `textarea` 占位「备注」。
  - 底栏：左侧关闭箭头（`closeTaskRightBox`）、中间「创建于{taskSetupTime}」、右侧删除任务图标（`v-show="!taskDisabled"`）。
- **交互清单**：
  - 完成勾选 change → `taskStore.changeIsFinished(dialog_right_config_Obj)` → 旧 `GET /task/record/complete`，新 `POST /tasks/{id}/complete` → 无 catch/回滚。
  - 标题 `@blur` → `updateRightItemHandler({type:'content'})` → `taskStore.updateTask` → 旧 `POST /task/record/update`，新 `PUT /tasks/{id}` → **content 为空时回退旧值并中止**；失败仅 `console.log`。
  - 收藏点击 → `taskStore.changeIsImportant` → `doCollect` / `delCollect` → `POST/DELETE /tasks/{id}/collect`。
  - 步骤：`addChilderTaskList`（Enter，`rightMaiTaskChilderValue` 非空才提交）→ `addStepTask` → 旧 `POST /task/subrecord/add`，新 `POST /tasks`（带 parentId）；`updateStepTaskFun`（Enter）→ `POST /task/subrecord/update`，新 `PUT /tasks/{id}`；`changeStepTaskFun`（勾选）→ `POST /task/subrecord/complete`，新 `POST /tasks/{id}/complete`；`delChilderTask`（二次确认）→ `POST /task/subrecord/del`，新 `DELETE /tasks/{id}`。每次成功后 `getStepTaskFun()` + `taskStore.getTskListByType()`。
  - 日期/提醒/重复下拉 `@confirm` → `updateRightItemHandler({type:'dateText'|'tipsText'|'eachText'})`；删除图标 → `closeValueByType(type)`（先清空字段再更新）。
  - 标签 `@change` → `updateRightItemHandler({type:'taskTypes'})`。
  - 附件：`handleBeforeUpload` 校验 → `handleUploadSuccess`/`handleUploadError`；点击文件名 `openFile` → `previewFile`；删除 `deleteFile`（splice + `updateRightItemHandler({type:'fileList'})`）。详见 `BbAttachmentList.md`。
  - 用户：点击行 `selectUserRef.show(userIdList)`；`confrimSelectUser` 将所选 `id`/`name` 以 `join(',')` 写入 `userIdList`/`userNameList` 并更新；删除图标 `deleteSelectedUsers` 置空后更新。
  - 备注 `@blur` → `updateRightItemHandler({type:'remark'})`。
  - 关闭 → `taskStore.closeTaskRightBox()`（置 `task_right_box_show=false`，`dialog_right_config_Obj=reactive({})`）。
  - 删除任务 → `$modal.confirm('是否确认删除任务?')` → `taskStore.deleteTaskList` → 旧 `GET /task/record/del`，新 `DELETE /tasks/{id}` → 成功 `msgSuccess('删除任务成功')` 后 `closeTaskRightBox()` → 取消/失败走 catch（无提示）。
  - `updateRightItemHandler` 统一逻辑：`content` 空 → 取 `bfRightItem` 旧值 return；订阅字段（`content/dateText/eachText/remark/userIdList/userNameList/taskTypes/tipsText`）新旧值相等 → return；否则 `taskStore.updateTask`，成功后刷新 `bfRightItem` 并 `getTaskTypeLen()`。
- **业务规则**：
  - **权限/只读**：`taskDisabled` 即「已完成 或 非归属人」，完成态任务与分配给他人的任务整单只读（步骤删除、附件删除、用户删除、任务删除均隐藏）。判定用严格相等，**id 类型必须一致**（见缺陷）。
  - 标签候选 `tagListOptions`：从 `tagTagStore.tagList` 中排除当前任务已选（`dialog_right_config_Obj.tagList` 的 id 集合）。
  - 提醒/重复/日期删除仅对**本人未完成任务**开放（依赖 `taskDisabled`）。
  - 步骤增删改查均以 `dialog_right_config_Obj.id` 为 `taskid`/`parentId`。
  - 上传大小上限 `fileSize = 5`(MB)，文件名不得与当前 `fileList` 重复。
- **边界与已知缺陷**：
  - `userStore.id !== belongUserId` 严格相等：`userStore.id` 来源不一（`getInfo` 取 `user.userId`、`getMyUserInfo` 取 `res.id`），若后端 `belongUserId` 为字符串/number 与前端不一致，将**误判为只读或误判为可编辑**（重写需统一类型并显式比较）。
  - `updateRightItemHandler` 对 `content` 空值强制回退旧值，**无法清空标题**；更新失败仅 `console.log`，无 UI 提示、无回滚。
  - `bfRightItem` 备份与 `dialog_right_config_Obj` 的拷贝时机松耦合（打开新任务时 store 直接替换对象，备份可能滞后），可能误判新旧值。
  - 步骤勾选使用 `:checked` 单向调用 `changeStepTask`（恒为完成方向），无相反方向接口（`changeStepTaskFun` 仅传 `id`）。
  - 标签删除模板 `closeTaskTypesByType` 已注释，仅保留 `el-select` 的 `multiple` 移除；删除用户 tooltip 文案误写为「删除提醒」。
  - `on`/`before-remove` 附件钩子被注释，删除附件无服务端二次确认（直接 splice + 更新）。
  - 抽屉显隐 `task_right_box_show` 由父 layout 消费，本组件不含 `el-drawer` 容器；**待确认**父级挂载组件与动画。
- **验收用例**：
  1. 打开本人未完成任务 → 各字段可编辑、显示删除任务与附件/用户删除入口。
  2. 打开已完成任务或他人任务 → `taskDisabled` 为真，标题/备注/下拉/上传/标签只读，删除入口隐藏，配置行出现遮罩。
  3. 清空标题失焦 → 不提交空值，恢复原文本。
  4. 修改任一订阅字段（如标签）→ 调用更新接口，成功后列表/计数刷新。
  5. 删除步骤/附件/任务 → 先二次确认，成功后刷新步骤与列表；取消不产生请求。
  6. 点击关闭箭头 → 右侧抽屉关闭且 `dialog_right_config_Obj` 清空。
