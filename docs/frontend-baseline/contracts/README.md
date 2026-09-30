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

`03 §8` M0 出口要求「**14 原子 + 16 业务 = 30 契约**」全部导出（该 30/30 已于冻结期达成）。**2026-09-29 产品决策（0307）：废弃 `BbOrgTree`**（`OrganizationalMechanismTree.vue`，旧实现「静态假数据 + 默认不渲染」的死组件）→ 业务契约 **16 → 15**，合计 **30 → 29**；其契约文件已删除。两类契约**交付形态不同**，计数口径明确如下：

| 类别 | 组件 | 契约形态 | 依据 |
|---|---|---|---|
| **业务组件（15）** | `BbTaskCard / BbTaskComposer / BbSubtaskList / BbCalendarCard / BbDatePicker / BbRemindSelect / BbRepeatSelect / BbUserSelect / BbCategoryTree / BbTagConfig / BbTaskList / BbTaskDetailPanel / BbTaskMetaLine / BbAttachmentList / BbParticipantList` | **行为契约**（本文模板 7 字段，基于**旧源码**导出） | 03 §2.3.1 |
| **原子组件（14）** | `BbButton / BbInput / BbSelect / BbModal / BbDrawer / BbTag / BbIcon / BbTree / BbTable / BbPagination / BbEmpty / BbTooltip / BbLoading / BbConfirm`（03 §3.4.1 列表） | **设计/样式契约**（Element Plus 薄封装，**无旧源可派生行为**）：props/emits 透传约定、Design Token 映射、间距/色/态（hover/disabled）、无障碍基准 | 03 §3.4.1（设计系统），非 §2.3.1 |

> 即：**15 份行为契约**走 §2.3 三步法（旧源导出 + 截图证据）；**14 份原子契约**走设计系统规格（Design Token/主题），同样入库 `contracts/` 但以「风格契约」形态，天然无旧源与截图比对需求。M0 验收按此两口径分别核验。

### 原子契约统一结构（0304/D6 归一）

14 份原子契约统一采用**项目符号体**（与业务契约模板同风格），字段顺序固定为：

```markdown
- 职责：
- 封装基线：（Element Plus 薄封装，透传 + BB 扩展边界）
- Props 透传约定：
- Emits / Slots / 方法：
- Design Token 映射：
- 状态矩阵：（default/hover/focus/active/disabled/loading/空态）
- 无障碍基准：
- 与旧实现的差异：
- 验收断言：（Vitest 快照 + 视觉基线）
```

## 组件契约清单

### 业务组件（15 · 行为契约）

| 旧组件（源码） | 新组件 | 契约 | 状态 |
|---|---|---|---|
| `todolistModule/components/TaskCard.vue` | `BbTaskCard` | `BbTaskCard.md` | ✅ |
| `todolistModule/components/addTaskBlock.vue` | `BbTaskComposer` | `BbTaskComposer.md` | ✅ |
| `todolistModule/components/childTaskList.vue` | `BbSubtaskList` | `BbSubtaskList.md` | ✅ |
| `todolistModule/components/CalendarCard.vue` | `BbCalendarCard` | `BbCalendarCard.md` | ✅ |
| `todolistModule/components/dropdownSetDate.vue` | `BbDatePicker` | `BbDatePicker.md` | ✅ |
| `todolistModule/components/dropdownSetTips.vue` | `BbRemindSelect` | `BbRemindSelect.md` | ✅ |
| `todolistModule/components/dropdownSetEach.vue` | `BbRepeatSelect` | `BbRepeatSelect.md` | ✅ |
| `todolistModule/components/selectUser.vue` | `BbUserSelect` | `BbUserSelect.md` | ✅ |
| `layoutNew/components/LeftBox/categoryTree.vue` | `BbCategoryTree` | `BbCategoryTree.md` | ✅ |
| `layoutNew/components/TagConfig/index.vue` | `BbTagConfig` | `BbTagConfig.md` | ✅ |
| `todolistModule/components/taskListOne.vue` / `taskListTwo.vue` | `BbTaskList` | `BbTaskList.md` | ✅ |
| `todolistModule/components/RightBoxDialog` / 详情 | `BbTaskDetailPanel`（原 `BbTaskDetailDrawer`，2026-09-30 更名为内联面板） | `BbTaskDetailDrawer.md` | ✅ |
| `TaskCard.vue`「元信息行」（日期/重复/提醒/标签/附件/@） | `BbTaskMetaLine` | `BbTaskMetaLine.md` | ✅ |
| `RightBoxDialog` el-upload + `.task-file-list` | `BbAttachmentList` | `BbAttachmentList.md` | ✅ |
| `TaskCard` `@userNameList` / `RightBoxDialog` 人员行 + `selectUser` | `BbParticipantList` | `BbParticipantList.md` | ✅ |

### 原子组件（14 · 设计/风格契约）

| 新组件 | 封装基线 | 契约 | 状态 |
|---|---|---|---|
| `BbButton` | `el-button` | `BbButton.md` | ✅ |
| `BbInput` | `el-input` | `BbInput.md` | ✅ |
| `BbSelect` | `el-select` | `BbSelect.md` | ✅ |
| `BbModal` | `el-dialog` | `BbModal.md` | ✅ |
| `BbDrawer` | `el-drawer` | `BbDrawer.md` | ✅ |
| `BbTag` | `el-tag` | `BbTag.md` | ✅ |
| `BbIcon` | `@element-plus/icons-vue` | `BbIcon.md` | ✅ |
| `BbTree` | `el-tree` | `BbTree.md` | ✅ |
| `BbTable` | `el-table` | `BbTable.md` | ✅ |
| `BbPagination` | `el-pagination` | `BbPagination.md` | ✅ |
| `BbEmpty` | `el-empty` | `BbEmpty.md` | ✅ |
| `BbTooltip` | `el-tooltip` | `BbTooltip.md` | ✅ |
| `BbLoading` | `v-loading` | `BbLoading.md` | ✅ |
| `BbConfirm` | `ElMessageBox.confirm` | `BbConfirm.md` | ✅ |

> 原子契约统一引用令牌命名 `--bb-color-* / --bb-space-* / --bb-radius-* / --bb-font-size-* / --bb-shadow-*`（03 §4.4），与实现期 `styles/tokens.css` 对齐。

> **完成度即 M0 出口硬标准（03 §8 / 修订 R15）**：**冻结期**全部 **30** 组件契约已导出、**30/30 达成**（历史）。**2026-09-29（0307）产品废弃 `BbOrgTree` 后，现行交付集合为 15 业务 + 14 原子 = 29。** 视觉证据（**38 PNG + 1 录屏**，含 `state-empty-*`/`state-error-*`/`state-httperr-*`）已采集于 `../screenshots/`，**按策略不入库**（`.gitignore`，0304/D3），由仓根 `../../scripts/golden-capture/` 复现。

## 旧接口 → 新接口 废弃映射（修订 L4；方法/路径校正见 0304/D1、补全见 0304/D6）

契约交互清单写**新** API（对齐 `03 §5.3.2`）。旧源码 `bluebird_task_Front/src/api/**` 的实际路径/方法已废弃，重写时按下表逐一对照。

> ⚠️ **方法校正（0304/D1）**：旧系统大量状态变更为 **HTTP GET**（`record.js` 中 `del/updateStatus/complete/completewithdraw/doCollect/delCollect` 与 `subrecord/del|complete` 均为 `method:'get'`）。此为旧实现缺陷（CSRF/预取/缓存风险，见 S4），**新系统一律用语义化方法**（以「新接口」列为准）。

### 任务（`api/todoList/record.js`，行号为该文件快照）

| 旧接口（方法 路径） | 新接口 | 备注 |
|---|---|---|
| `POST /task/record/add`（L12） | `POST /tasks` | 建主任务 |
| `POST /task/record/update`（L30） | `PUT /tasks/{id}` | |
| `GET /task/record/del`（L40） | `DELETE /tasks/{id}` | 旧为 GET（缺陷） |
| `GET /task/record/updateStatus`（L50） | `PUT /tasks/{id}` | status 归入更新；旧为 GET |
| `GET /task/record/complete`（L60） | `POST /tasks/{id}/complete` | recurring 传 dueAt；旧为 GET |
| `GET /task/record/completewithdraw`（L70） | `POST /tasks/{id}/uncomplete` | 旧为 GET |
| `GET /task/record/doCollect`（L80） | `POST /tasks/{id}/collect` | 旧为 GET |
| `GET /task/record/delCollect`（L90） | `DELETE /tasks/{id}/collect` | 旧为 GET |
| `GET /task/record/count`（L100） | `GET /tasks/count` | |
| `GET /task/record/day`（L108） | `GET /tasks?scope=day` | |
| `GET /task/record/week`（L117） | `GET /tasks?scope=week` | |
| `GET /task/record/do`（L126） | `GET /tasks?scope=assigned` | 路由 `/myDo` |
| `GET /task/record/join`（L135） | `GET /tasks?scope=joined` | 路由 `/myJoin` |
| `GET /task/record/collect`（L144） | `GET /tasks?scope=collect` | |
| `GET /task/record/link`（L153） | `GET /tasks?scope=all` | **印证 `LINK`=全部任务（allTask）**，非「任务关联」 |
| `GET /task/record/getproxycalendar`（L178） | `GET /tasks/calendar?start=&end=` | 日历（周期实例展开） |
| `GET /task/record/getrecordbydata`（L186） | `GET /tasks?scope=all&date=` | 点日历某日过滤 |
| `POST /task/subrecord/add`（L195） | `POST /tasks`（带 parentId） | 子任务 |
| `GET /task/subrecord/getlist`（L206） | `GET /tasks/subtasks?parentId=` | |
| `GET /task/subrecord/del`（L214） | `DELETE /tasks/{id}` | 旧为 GET |
| `POST /task/subrecord/update`（L222） | `PUT /tasks/{id}` | |
| `GET /task/subrecord/complete`（L230） | `POST /tasks/{id}/complete` | 旧为 GET |

### 自定义栏 / 分类 / 标签 / 人员 / 附件（其余模块 · 0304/D6 补全）

| 旧接口（方法 路径） | 新接口 | 备注 |
|---|---|---|
| `POST /task/menu/add`（`record.js` L161） | `POST /menus` | 新增自定义栏 |
| `GET /task/menu/getmenulist`（L169） | `GET /menus` | |
| `POST /task/menu/doCollect`（L21） | `POST /menus/{id}/items` | 移动任务到栏 |
| `GET /sys/category/tree` | `GET /categories` | 分类树 |
| `POST /sys/category/add` | `POST /categories` | 新增分类 |
| `POST /sys/category/update` | `PUT /categories/{id}` | |
| `GET /sys/category/del` | `DELETE /categories/{id}` | 旧为 GET |
| `GET /sysTag/page`（`api/sysTag.js` L15） | `GET /tags` | 标签列表 |
| `POST /sysTag/add`（L41） | `POST /tags` | |
| `POST /sysTag/update`（L54） | `PUT /tags/{id}` | |
| `GET /sysTag/del?ids=`（L63） | `DELETE /tags/{id}` | 旧为 GET |
| `GET /admin/user/list` | `GET /users?deptId=&keyword=` | 人员选择（`BbUserSelect`） |
| `GET /admin/user/getListByIds` | `GET /users?ids=` | 回显已选（`BbUserSelect`） |
| `GET /admin/user/myself` | `GET /users/me` | 当前用户 |
| `GET /sys/file/download/{id}`（`utils.js` L149） | `GET /files/{id}` | 附件下载；预览走 `/files/{id}/preview` |
| `POST /sys/file/upload`（**待与旧源码核对**） | `POST /files` | 附件上传 |

> **现状态 vs 目标态（0304/§2）**：上表「新接口」为**重构目标契约**（`/api/v1` + 成功码 `code==0`，见 `02 §1.3`），**非现有系统**。旧系统为 `/task/record/*` 裸 `Page`/裸实体、成功码由前端按「缺失即 200」解释（`utils/request.js`）。二者不可混用；openapi 只描述目标态，故不得据其判断旧系统行为。
