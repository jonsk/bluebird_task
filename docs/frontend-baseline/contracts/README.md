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
