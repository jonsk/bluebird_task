# 设计契约：BbSelect（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一下拉选择器（单选/多选/可筛选/可清除），承载枚举与字典类录入。
- 封装基线：基于 `el-select`（+ `el-option` 约定）。封装原因：旧前端下拉的 popper 宽度、空态文案、清除图标与选中态颜色不统一，需统一 token 与「空数据」提示，并固定 teleport/层级行为。
- Props 透传约定：透传 `model-value`（`v-model`）、`multiple`、`collapse-tags`、`collapse-tags-tooltip`、`filterable`、`clearable`、`disabled`、`size`、`placeholder`、`multiple-limit`、`allow-create`、`default-first-option`、`remote`、`remote-method`、`loading`、`loading-text`、`no-data-text`、`no-match-text`、`reserve-keyword`、`popper-class`、`teleported`。BB 扩展 props：`bb-size`（`sm|md|lg`，默认 `md`）、`bb-empty-text`（默认「暂无数据」，覆盖 `no-data-text`，统一空态）。
- Emits / Slots / 方法：透传 `update:modelValue`、`change`、`visible-change`、`focus`、`blur`、`clear`、`remove-tag`；透传 `default`（`el-option`）、`prefix`、`empty`、`loading` slot；透传 `focus()`/`blur()` 等方法。
- Design Token 映射：
  - 触发器：边框 `--bb-color-border`（focus `--bb-color-primary`，disabled `--bb-color-border`）
  - 底色：`--bb-color-bg`；下拉面板底 `--bb-color-bg-elevated`
  - 文本：选中 `--bb-color-text-primary`；placeholder/空态 `--bb-color-text-secondary`；禁用 `--bb-color-text-disabled`
  - 选中项/高亮：`--bb-color-primary`（文字或浅底）
  - 圆角：`--bb-radius-md`（触发器）/ `--bb-radius-sm`（选项）
  - 间距：`--bb-space-2|3`（内边距/选项高度）、`--bb-space-2`（tag 间隙）
  - 字号：`--bb-font-size-sm|md|lg`
  - 阴影：下拉面板 `--bb-shadow-md`（或 `--bb-shadow-lg`，由 `popper-class` 统一）
- 状态矩阵：
  - default：`--bb-color-border`
  - hover：边框弱提升
  - focus/active：边框 `--bb-color-primary` 且面板展开，focus ring 可见
  - disabled：文字 `--bb-color-text-disabled`、底 `--bb-color-bg-elevated`、不可展开
  - loading：触发器 `loading-text`，面板内 spinner，禁止重复选择
  - 空态：`bb-empty-text` 居中，`--bb-color-text-secondary`
- 无障碍基准：触发器 `role="combobox"`、`aria-expanded`、`aria-haspopup="listbox"`；列表 `role="listbox"`、选项 `role="option"` 与 `aria-selected`；键盘 ↑/↓ 移动、Enter 选中、Esc 关闭、Tab 离开；`aria-label`/关联 `label` 必填；focus ring 可见；正文对比度 ≥ 4.5:1。
- 与旧实现的差异：收敛旧前端 `popper-class` 中重复的宽高/颜色样式与各页自行拼接的空态；统一 `bucket` 空态与面板阴影 token，禁止散写 `#fff`/`#606266` 等硬编码。
- 验收断言：Vitest 快照覆盖 default/disabled/loading/空态（断言 `aria-expanded`、`role`、空态文案）；视觉基线断言触发器边框/圆角、面板阴影、选中项高亮与 token 一致；键盘操作可选中断言。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；令牌命名全批统一。
