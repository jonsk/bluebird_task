# 设计契约：BbEmpty（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- **职责**：作为六大视图与详情/搜索等场景的**统一空态载体**（对应旧前端空列表渲染 `state-empty-*`）。
- **封装基线**：基于 `el-empty`
- **Props 透传约定**：
  - 直接透传：`image`、`image-size`、`description`。
  - 本组件新增：`variant: 'list' | 'detail' | 'search'`（列表空/详情无数据/搜索无结果，控制插图尺寸与文案默认值）、`size: 'default' | 'small'`。
  - 六大视图经 `description` 传入视图语境文案（如「暂无任务」「还没有 @ 你的任务」），**空态文案与旧 `state-empty-*` 截图语义对齐**。
- **Emits / Slots / 方法**：
  - Emits：无（纯展示）。
  - Slots（透传）：`default`（覆盖 description）、`image`（自定义插图）、`footer`（**放置 CTA，如「新建任务」按钮 `BbButton`**）。
  - 方法：无。
- **Design Token 映射**：
  - 描述文字：`--bb-color-text-secondary`；兜底主标题（若业务传入）：`--bb-color-text-primary`。
  - 插图/占位图标：`--bb-color-text-disabled` 与 `--bb-color-border` 描边。
  - 间距：容器上下留白 `--bb-space-5` / `--bb-space-6`；图文间距 `--bb-space-4`。
  - 字号：description `--bb-font-size-md`（list/detail）/ `--bb-font-size-sm`（search）。
- **状态矩阵**：`default` 常规空态 ｜ `hover` / `active` 不适用（无交互；若 `footer` 内放置按钮，按钮状态由 `BbButton` 承担） ｜ `disabled` 不适用 ｜ `loading` 由外层 `BbLoading` 覆盖，加载期间不渲染空态。
- **无障碍基准**：
  - 容器 `role="status"`、`aria-live="polite"`，确保空态出现时被读屏播报；文案通过 `description` 提供可读文本。
  - 插图若为纯装饰，`aria-hidden="true"`；`footer` 内 CTA 为可聚焦控件并具备 `aria-label`。
  - 文字与背景对比度 ≥ 4.5:1。
- **与旧实现的差异**：旧前端六大视图空态为各视图手写或缺失、无统一文案与结构（见 `screenshots/README.md` 的 `state-empty-*`）；新组件统一视觉与文案，并支持在空态直接给出 CTA（旧无）。
- **验收断言**：
  - Vitest 快照断言 `role="status"`、description 文案、插图容器与 `footer` 插槽结构。
  - 视觉基线（§2.3.2 G7）：逐视图对应 `state-empty-{day,week,joined,assigned,collect,all}-1440x900.png`，结构化断言文案、插图尺寸/留白；像素 diff 作告警。
