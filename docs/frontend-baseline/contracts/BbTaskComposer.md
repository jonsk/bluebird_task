# 行为契约：addTaskBlock.vue → BbTaskComposer

> 源：`bluebird_task_Front/src/views/todolistModule/components/addTaskBlock.vue`（已读源码 L1-278；行号为导出时快照，以方法名/类名/字段名为准）

| 项 | 契约内容 |
|---|---|
| **用途** | 顶部「新增任务」输入块；`editType="child"` 时复用为「新增子任务」（子模式无上边距/顶分隔线，`child` 样式 `addTaskBlock.vue` L213-216） |
| **输入/状态来源** | `props.editType`（`"child"`=子任务，否则主任务）；`route.path`（`/index,/myWeek,/myCollect,/myJoin,/myDo,/allTask` 六常驻视图 vs 自定义栏路径）；`taskStore`；子组件 `dropdownSetDate/dropdownSetTips/dropdownSetEach/selectUser` |
| **展示规则** | 输入框 placeholder：子模式「添加子任务」否则「添加任务」（L22）；聚焦（`@focus`）展开配置行（L20/127：`inputBoxSetConfigShowFlag=true`）；输入非空时启用「添加」按钮（`inputBoxInputHandler` L130-132，`inputBoxSetConfigSetFlag = !task.length`，L91 禁用）；配置行含 截止日期/提醒/重复/分配（@）四项 + 添加按钮（L24-95） |
| **交互清单** | ① Enter / 点「添加」→ `addTaskList()`（L146）：空串直接 return；② 收集 `taskObj`（content/dateText/tipsText/eachText/remark/fileList/taskTypes/userIdList/userNameList/isImportant/menuid）→ `taskStore.addTaskList().then(() => emit('addSuccess'))`（L174-177）；③ 成功 emit `addSuccess` 刷新列表；④ 失败：then 无 catch（旧缺陷，重写需补错误提示/回滚） |
| **业务规则** | 收藏视图（`/myCollect`）新建任务 `isImportant=true`（默认收藏，L151-152）；六常驻视图 `menuid=''`，自定义栏路径则 `menuid=route.path.split('/')[1]`（L136-141）；分配人 `select-user` 确认后以 `names.join(',')`/`ids.join(',')` 存（L189-199）；`eachText` 周期语义（每天_0 每周_1 每月_2 每年_3，L162） |
| **边界与已知缺陷** | ① 提交后 `addTaskList` 的 promise **无 .catch**：失败静默且输入已清空（重写需补失败提示 + 不清空或回滚）；② 「添加」按钮依赖 `task.length` 判断（`v-model.trim` 后空串判定在方法内重复校验）；③ 每次提交后重置全部 dropdown 文本与 `isImportant=''`（L178-184，收藏态重置为字符串空值，重写改 `false`） |
| **验收用例** | 1) 空输入不触发新增；2) 主视图新建 → 调 `/tasks` POST → 列表刷新；3) 收藏视图新建默认收藏；4) 自定义栏路径新建带 `menuid`；5) Enter 与点「添加」等价；6) 子模式 placeholder/样式为子任务；7) 提交失败有提示且不丢输入 |

> 新语义：`dateText`=due_at、`tipsText`=remind_at、`eachText`→`cycle_rule`（全新格式 `{freq,interval,dtstart,...}`，见 02 §4.3）、分配→`assigneeIds`、`belongUserId`→`owner.id`。截图/录屏证据已采集（见 `../screenshots/README.md`；按策略**不入库**，由 `../../scripts/golden-capture/` 复现）。
