# 设计契约：BbLoading（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：提供统一的加载态入口，覆盖**列表加载**与**详情加载**（局部遮罩 + 可选服务式全屏）。
- 封装基线：基于 `el-loading`（`v-loading` 指令 + `ElLoading.service`）
- Props 透传约定：
  - 组件式 `<BbLoading>`：`loading: boolean`、`text: string`、`spinner`、`background`、`customClass`、`target`、`lock`（加载时锁定滚动）、`variant: 'list' | 'detail'`（控制默认文案与尺寸）。
  - 指令式：`v-loading` 的 `element-loading-text` / `element-loading-spinner` / `element-loading-background` / `element-loading-custom-class` 绑定透传。
  - 服务式：`BbLoading.service(options)` 返回实例，支持 `close()`。
  - 默认值：`background` 使用 token 背景（半透明），`text` 由 `variant` 决定（列表「加载中…」/ 详情「加载详情…」）。
- Emits / Slots / 方法：
  - Emits：无。
  - Slots：`default`（被包裹内容；`loading` 为真时叠加遮罩）。
  - 方法：服务式实例 `close()`；组件式无对外方法。
- Design Token 映射：
  - 遮罩背景：`--bb-color-bg`（带透明度，如 90%）；加载文字：`--bb-color-text-secondary`；spinner：`--bb-color-primary`。
  - 圆角：`--bb-radius-md`；文字字号：`--bb-font-size-sm`；遮罩内间距：`--bb-space-4`。
- 状态矩阵：`default`（`loading=false`）仅渲染内容 ｜ `loading`（`loading=true`）叠加遮罩、`aria-busy="true"` ｜ `hover` / `active` / `disabled` 不适用（遮罩期间不响应交互） ｜ 局部（list/detail）为默认，避免使用全屏遮罩。
- 无障碍基准：
  - 遮罩容器 `role="status"`、`aria-busy="true"`、`aria-live="polite"`，确保读屏感知加载。
  - 遮罩不夺取焦点；加载期间对底层内容设为不可交互（`pointer-events` 与 `aria-disabled`），避免误操作。
  - 遵循 `prefers-reduced-motion: reduce`，降低/关闭 spinner 动画。
  - 加载文字与遮罩背景对比度 ≥ 4.5:1。
- 与旧实现的差异：旧前端 loading 散落各处、样式与文案不统一，且错误态提示堆叠（见 `screenshots/README.md` 缺陷留痕）；新组件统一 token、统一文案，并提供列表/详情两种局部形态，减少全局遮罩滥用。
- 验收断言：
  - Vitest 快照断言遮罩 DOM 结构、`role="status"`、`aria-busy` 与加载文案；对照 `variant` 判定文案。
  - 视觉基线（§2.3.2 G7）：结构化断言遮罩背景/透明度与 spinner 色（primary）；像素 diff 作告警。
