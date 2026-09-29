# 设计契约：BbButton（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一按钮，承载主/次/危险/文字等操作语义与统一交互态。
- 封装基线：基于 `el-button`。封装原因：旧前端按钮色值/尺寸/圆角散落硬编码，需收敛到 `--bb-*` token 与统一意图色（含 `--bb-color-overdue`/`--bb-color-near` 的业务语义），并统一 loading/disabled 态与间距。
- Props 透传约定：透传 `type`（`primary|success|warning|danger|info|default`）、`size`、`plain`、`round`、`circle`、`text`、`link`、`native-type`、`disabled`、`loading`、`loading-icon`、`icon`、`autofocus`。BB 扩展 props：`bb-block`（Boolean，默认 `false`，整行宽度）、`bb-intent`（`primary|success|warning|danger|overdue|near|default`，默认 `default`；`overdue`/`near` 映射业务 token，覆盖 `type`）、`bb-size`（`sm|md|lg`，默认 `md`，映射 `--bb-space-*` 高度）。
- Emits / Slots / 方法：透传 `click`；透传默认 slot 与 `icon` slot；透传 `el-button` 公开方法（`ref.focus()`/`ref.blur()`/`ref.$el`），不新增方法。
- Design Token 映射：
  - 主色：`--bb-color-primary` / hover `--bb-color-primary-hover` / active `--bb-color-primary-active`
  - 语义色：`--bb-color-success` / `--bb-color-warning` / `--bb-color-danger`
  - 业务色（`bb-intent=overdue|near`）：`--bb-color-overdue` / `--bb-color-near`
  - 文本：`--bb-color-text-primary` / `--bb-color-text-secondary` / `--bb-color-text-disabled`
  - 底色/边框：`--bb-color-bg` / `--bb-color-bg-elevated` / `--bb-color-border`
  - 圆角：`--bb-radius-sm`（sm）/ `--bb-radius-md`（md）/ `--bb-radius-lg`（lg/circle 基准）
  - 间距：`--bb-space-2|3|4`（padding）、`--bb-space-2`（icon 与文字间隙）、`--bb-space-1`（sm）
  - 字号：`--bb-font-size-sm`（sm）/ `--bb-font-size-md`（md）/ `--bb-font-size-lg`（lg）
  - 阴影：`--bb-shadow-sm`（默认/hover 提升）
- 状态矩阵：
  - default：主色实底；次/文字按钮透明底 + `--bb-color-border` 或主色文字
  - hover：底色 → `--bb-color-primary-hover`（或意图色 hover 档），阴影 `--bb-shadow-sm`
  - active：底色 → `--bb-color-primary-active`，无位移阴影
  - disabled：文字 `--bb-color-text-disabled`，底/边 `--bb-color-border`，`cursor:not-allowed`，不响应 `click`
  - loading：内置转圈，禁止重复触发，`aria-busy="true"`，保留原宽度防抖动
- 无障碍基准：原生 `<button>` 语义；键盘 Enter/Space 触发，`Tab` 可达且 focus 可见；focus ring 统一 `outline:2px solid var(--bb-color-primary); outline-offset:2px`；纯图标按钮必须提供 `aria-label`；正文/按钮文字与底色的对比度 ≥ 4.5:1，禁用态可豁免但须可辨识；`loading` 时 `aria-busy`。
- 与旧实现的差异：收敛旧前端散落的硬编码十六进制主色、`var(--bg-primary)` 类非标准变量与逐处内联 `style` 按钮；`overdue/near` 等业务语义色统一从 token 取，禁止组件内硬编码红/绿。
- 验收断言：Vitest + `@vue/test-utils` 对 default/hover/active/disabled/loading 五态做结构快照（断言类名、`aria-busy`、`disabled` 属性、文案，不硬断 EP 内部样式栈）；视觉基线（03 §2.3.2 G7）断言主/次/危险按钮的底色、圆角、字号与设计令牌一致，focus ring 可见。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；令牌命名全批统一，实现 `styles/tokens.css` 后本契约即生效。
