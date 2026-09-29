# 设计契约：BbPagination（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：列表分页控制，统一页大小选项、布局与激活态外观。
- 封装基线：基于 `el-pagination`
- Props 透传约定：
  - 直接透传：`total`、`page-count`、`pager-count`、`layout`、`page-sizes`、`background`、`small`、`hide-on-single-page`、`disabled`、`prev-text`、`next-text`。
  - 双向绑定封装：暴露并透传 `v-model:current-page`、`v-model:page-size`。
  - 本组件默认值：`layout = 'total, sizes, prev, pager, next, jumper'`、`background = true`、`page-sizes = [10, 20, 50, 100]`，可在业务侧覆盖。
- Emits / Slots / 方法：
  - Emits：`update:current-page`、`update:page-size`、`current-change`、`size-change`、`change`（透传）。
  - Slots（透传）：`default`（自定义内容）。
  - 方法：无独立方法，经 `v-model` 反馈页码状态。
- Design Token 映射：
  - 激活页码背景/文字：`--bb-color-primary`；悬停：`--bb-color-primary-hover`；按下：`--bb-color-primary-active`。
  - 页码边框：`--bb-color-border`；文字：`--bb-color-text-primary`；禁用：`--bb-color-text-disabled`。
  - 圆角：`--bb-radius-sm`；间距：`--bb-space-2` / `--bb-space-3`；字号：`--bb-font-size-sm` / `--bb-font-size-md`。
- 状态矩阵：`default` 常规页码 ｜ `hover` `--bb-color-primary-hover` 描边/文字 ｜ `active` 背景 `--bb-color-primary` + 白字并 `aria-current="page"` ｜ `disabled` 文字 `--bb-color-text-disabled` ｜ `loading` 不适用（分页自身无加载态，列表加载由 `BbLoading` 承担）。
- 无障碍基准：
  - 容器 `role="navigation"` 且 `aria-label="分页"`；当前页按钮 `aria-current="page"`；上一/下一页 `aria-label` 可读。
  - 键盘可达：所有页码与跳转输入可 Tab 聚焦；`focus ring` 使用 `2px` `--bb-color-primary`；`Enter` 触发跳转。
  - 激活页码文字/背景对比度 ≥ 4.5:1。
- 与旧实现的差异：旧若依分页组件与主题变量随脚手架移除；新组件统一 `background` 外观、统一页大小选项与布局，禁止业务侧散写分页样式。
- 验收断言：
  - Vitest 快照断言总条数文案、页码结构、`aria-current` 与禁用态。
  - 视觉基线（§2.3.2 G7）：结构化断言激活页码色（primary）、边框与间距；像素 diff 作告警。
