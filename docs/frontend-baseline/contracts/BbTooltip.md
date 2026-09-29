# 设计契约：BbTooltip（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- **职责**：为超长文本与图标说明提供统一悬浮提示（任务卡 `@人员`、标签、日期等省略内容）。
- **封装基线**：基于 `el-tooltip`
- **Props 透传约定**：
  - 直接透传：`content`、`placement`、`effect`、`trigger`（默认 `hover`）、`disabled`、`offset`、`show-after`、`hide-after`、`popper-class`、`teleported`、`enterable`、`raw-content`。
  - 本组件新增：`maxWidth`（气泡最大宽度，默认约 240px，配合文本换行）、`delay`（统一触发延迟，默认 100ms，映射到 `show-after`/`hide-after`）。
  - 约定：**内容必须可读**，`content` 为空或与 `default` 文本相同时可经 `disabled` 关闭，避免噪音气泡。
- **Emits / Slots / 方法**：
  - Emits（透传）：无独立业务事件（`el-tooltip` 无对外 emit）。
  - Slots：`default`（触发元素，必填）、`content`（富内容，替代 `content` prop）。
  - 方法：暴露 `updatePopper()` / `show()` / `hide()`（经 `defineExpose` 转发）。
- **Design Token 映射**：
  - 气泡背景：`--bb-color-bg-elevated`（浅色主题）或深色语义底色；气泡文字：`--bb-color-text-primary`。
  - 圆角：`--bb-radius-md`；阴影：`--bb-shadow-md`；内边距：`--bb-space-2` / `--bb-space-3`；字号：`--bb-font-size-sm`。
  - 箭头颜色随气泡背景一致（禁止遗留 EP 默认黑）。
- **状态矩阵**：`default` 隐藏 ｜ `hover` 显示气泡（或 `focus` 触发） ｜ `active` 触发元素按下态由被包裹元素自身承担 ｜ `disabled`（`disabled=true`）不显示气泡 ｜ `loading` 不适用。
- **无障碍基准**：
  - 气泡 `role="tooltip"`，并给触发元素设置 `aria-describedby` 指向气泡内容。
  - 键盘可达：`trigger` 支持 `focus`，纯图标触发元素须可聚焦（`tabindex="0"`）并有 `aria-label`。
  - 气泡内容为只读描述，不承载可交互控件（如需交互改用 `el-popover` 语义）。
  - 文字与气泡背景对比度 ≥ 4.5:1。
- **与旧实现的差异**：旧前端用原生 `title` 或散落硬编码提示，样式与触发方式不一、无 focus 可达；新组件统一 popper 主题（走 token）、统一延迟与最大宽度，并补齐键盘可达。
- **验收断言**：
  - Vitest 快照断言触发元素存在、`aria-describedby` 关联与气泡内容；对 popper 挂载位置不做长度级硬断言（§7 R16）。
  - 视觉基线（§2.3.2 G7）：结构化断言气泡背景色、圆角、文字色；像素 diff 作告警。
