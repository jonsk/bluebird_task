# 行为契约（Behavior Contracts）索引

每个旧手写组件一份契约，作为重写 `Bb` 组件的**验收依据**（03 §2.3.1）。契约必须基于旧源码导出并在旧前端可运行期间补齐截图/录屏证据。

## 契约模板

```markdown
# 行为契约：<旧组件名> → <新组件名>
- 用途：
- 输入/状态来源：props / inject / store / 全局
- 展示规则：（含着色、格式化、条件显示）
- 交互清单：<动作> → <效果> → <接口> → <异常/回滚>
- 业务规则：（阈值、权限、去重等硬规则）
- 边界与已知缺陷：（旧实现 bug，重写是否修正）
- 验收用例：（可直接转为 E2E 步骤）
```

> **行号约定（修订 L2）**：契约中引用的旧源码行号为**导出时快照**，可能随源码微调漂移（1–8 行）。复核/导航以 **方法名 / 类名 / 字段名 / 字符串常量** 为准，行号仅作近似定位。

## 契约范围与计数口径（修订 L3）

`03 §8` M0 出口要求「**14 原子 + 16 业务 = 30 契约**」全部导出。两类契约的**交付形态不同**，计数口径明确如下：

| 类别 | 组件 | 契约形态 | 依据 |
|---|---|---|---|
| **业务组件（16）** | `BbTaskCard / BbTaskComposer / BbSubtaskList / BbCalendarCard / BbDatePicker / BbRemindSelect / BbRepeatSelect / BbUserSelect / BbCategoryTree / BbOrgTree / BbTagConfig / BbTaskList / BbTaskDetailDrawer / BbTaskMetaLine / BbAttachmentList / BbParticipantList` | **行为契约**（本文模板 7 字段，基于**旧源码**导出） | 03 §2.3.1 |
| **原子组件（14）** | `BbButton / BbInput / BbSelect / BbModal / BbDrawer / BbTag / BbIcon / BbTree / BbTable / BbPagination / BbEmpty / BbTooltip / BbLoading / BbConfirm`（03 §3.4.1 列表） | **设计/样式契约**（Element Plus 薄封装，**无旧源可派生行为**）：props/emits 透传约定、Design Token 映射、间距/色/态（hover/disabled）、无障碍基准 | 03 §3.4.1（设计系统），非 §2.3.1 |

> 即：**16 份行为契约**走 §2.3 三步法（旧源导出 + 截图证据）；**14 份原子契约**走设计系统规格（Design Token/主题），同样入库 `contracts/` 但以「风格契约」形态，天然无旧源与截图比对需求。M0 验收按此两口径分别核验。

## 组件契约清单

| 旧组件（源码） | 新组件 | 契约 | 状态 |
|---|---|---|---|
| `todolistModule/components/TaskCard.vue` | `BbTaskCard` | `BbTaskCard.md` | ✅ |
| `todolistModule/components/addTaskBlock.vue` | `BbTaskComposer` | `BbTaskComposer.md` | ✅ |
| `todolistModule/components/childTaskList.vue` | `BbSubtaskList` | `BbSubtaskList.md` | ✅ |
| `todolistModule/components/CalendarCard.vue` | `BbCalendarCard` | `BbCalendarCard.md` | ✅ |
| `todolistModule/components/dropdownSetDate.vue` | `BbDatePicker` | `BbDatePicker.md` | ✅ |
| `todolistModule/components/dropdownSetTips.vue` | `BbRemindSelect` | （待导出） | ⛔ |
| `todolistModule/components/dropdownSetEach.vue` | `BbRepeatSelect` | （待导出） | ⛔ |
| `todolistModule/components/selectUser.vue` | `BbUserSelect` | （待导出） | ⛔ |
| `layoutNew/components/LeftBox/categoryTree.vue` | `BbCategoryTree` | （待导出） | ⛔ |
| `layoutNew/components/LeftBox/OrganizationalMechanismTree.vue` | `BbOrgTree` | （待导出） | ⛔ |
| `layoutNew/components/TagConfig/index.vue` | `BbTagConfig` | （待导出） | ⛔ |
| `todolistModule/components/taskListOne.vue` / `taskListTwo.vue` | `BbTaskList` | （待导出） | ⛔ |
| `todolistModule/components/RightBoxDialog` / 详情 | `BbTaskDetailDrawer` | （待导出） | ⛔ |
| （对话/详情表单字段） | `BbTaskMetaLine` | （待导出） | ⛔ |
| 附件 | `BbAttachmentList` | （待导出） | ⛔ |
| 参与人展示 | `BbParticipantList` | （待导出） | ⛔ |
| …其余（见 03 §1.1 完整清单） | 14 原子 + 16 业务 | — | 待导出 |

> **完成度即 M0 出口硬标准（03 §8 / 修订 R15）**：全部 30 组件契约导出后才放行 M1。上表 ⛔ 为待办。

## 旧接口 → 新接口 废弃映射（修订 L4）

契约交互清单写**新** API（对齐 `03 §5.3.2`）。旧源码 `bluebird_task_Front/src/api/todoList/record.js` 的实际路径已废弃，重写时按下表逐一对照（行号为该文件快照）：

| 旧接口（record.js） | 新接口 | 备注 |
|---|---|---|
| `POST /task/record/add`（L15） | `POST /tasks` | 建主任务 |
| `POST /task/record/update`（L33） | `PUT /tasks/{id}` | |
| `POST /task/record/del`（L43） | `DELETE /tasks/{id}` | |
| `POST /task/record/updateStatus`（L53） | `PUT /tasks/{id}` | status 归入更新 |
| `POST /task/record/complete`（L63） | `POST /tasks/{id}/complete` | recurring 传 dueAt |
| `POST /task/record/completewithdraw`（L73） | `POST /tasks/{id}/uncomplete` | |
| `POST /task/record/doCollect`（L83） | `POST /tasks/{id}/collect` | |
| `POST /task/record/delCollect`（L93） | `DELETE /tasks/{id}/collect` | |
| `GET /task/record/count`（L102） | `GET /tasks/count` | |
| `GET /task/record/day`（L110） | `GET /tasks?scope=day` | |
| `GET /task/record/week`（L119） | `GET /tasks?scope=week` | |
| `GET /task/record/do`（L128） | `GET /tasks?scope=assigned` | 路由 `/myDo` |
| `GET /task/record/join`（L137） | `GET /tasks?scope=joined` | 路由 `/myJoin` |
| `GET /task/record/collect`（L146） | `GET /tasks?scope=collect` | |
| `GET /task/record/link`（L155） | `GET /tasks?scope=all` | **印证 `LINK`=全部任务（allTask）**，非「任务关联」 |
| `POST /task/menu/add`（L163） | `POST /menus` | 自定义栏 |
| `GET /task/menu/getmenulist`（L171） | `GET /menus` | |
| `POST /task/menu/doCollect`（L24） | `POST /menus/{id}/items` | 移动任务到栏 |
| `GET /task/record/getproxycalendar`（L180） | `GET /tasks/calendar?start=&end=` | 日历（周期实例展开） |
| `GET /task/record/getrecordbydata`（L188） | `GET /tasks?scope=all&date=` | 点日历某日过滤 |
| `POST /task/subrecord/add`（L197） | `POST /tasks`（带 parentId） | 子任务 |
| `GET /task/subrecord/getlist`（L206） | `GET /tasks/subtasks?parentId=` | |
| `POST /task/subrecord/del`（L214） | `DELETE /tasks/{id}` | |
| `POST /task/subrecord/update`（L222） | `PUT /tasks/{id}` | |
| `POST /task/subrecord/complete`（L232） | `POST /tasks/{id}/complete` | |
