# 设计契约：BbIcon（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一图标渲染入口，统一图标尺寸、颜色（含语义/业务色）与可访问性。
- 封装基线：基于 `@element-plus/icons-vue`（`el-icon` 容器）。封装原因：旧前端图标来源混杂、尺寸与颜色逐处内联 `font-size`/`color`，且业务态图标（逾期/临期）与装饰/功能图标未区分；需统一 token 尺寸/色并强制可访问性约定。
- Props 透传约定：内核透传 EP 图标组件为默认 slot（`<el-icon><component :is="name"/></el-icon>`）；`el-icon` 的 `size`、`color` 透传。BB 扩展 props：`name`（String，必填，注册的 EP 图标名，如 `Calendar`）、`bb-size`（`sm|md|lg|xl`，默认 `md`，映射 `--bb-font-size-sm|md|lg` 与固定 px 档）、`bb-color`（`default|primary|success|warning|danger|overdue|near|secondary|disabled`，默认 `default`）、`bb-spin`（Boolean，默认 `false`，旋转动画，如加载/刷新）、`bb-label`（String，可选，提供时为功能性图标并渲染 `aria-label`/`role="img"`）。
- Emits / Slots / 方法：透传 `click`；默认 slot 可覆盖 `name`（允许直接传入图标组件）；无专属方法。
- Design Token 映射：
  - 颜色：默认/正文 `--bb-color-text-primary`；`secondary`→`--bb-color-text-secondary`；`disabled`→`--bb-color-text-disabled`；`primary`→`--bb-color-primary`；`success`→`--bb-color-success`；`warning`→`--bb-color-warning`；`danger`→`--bb-color-danger`；`overdue`→`--bb-color-overdue`；`near`→`--bb-color-near`
  - 尺寸：`--bb-font-size-sm`（sm）/ `--bb-font-size-md`（md）/ `--bb-font-size-lg`（lg）；`xl` 取 `--bb-space-5` 量级对应 px 档
  - 间距：`--bb-space-1|2`（图标与相邻文字的间隙，建议由父容器统一）
  - 圆角/阴影：不适用（图标本身不设）
- 状态矩阵：
  - default：`--bb-color-text-primary`，按 `bb-size`
  - hover：继承可点击父级（如按钮/链接）的 hover 态；`bb-spin` 时持续旋转
  - active：继承父级 active 态；图标本身无位移
  - disabled：`--bb-color-text-disabled`，`cursor:not-allowed`（父级控制交互）
  - loading：以 `bb-spin` 表达（如刷新/加载图标），并可配 `aria-busy` 于父级
- 无障碍基准：**装饰性图标**（无 `bb-label`）加 `aria-hidden="true"` 且不参与 tab 序；**功能性图标**（有 `bb-label`）加 `role="img"` 与 `aria-label`，可被读屏识别；颜色不得为唯一信息载体（逾期/临期须辅以文字或 tooltip）；图标与背景对比度 ≥ 3:1（图形元素基准）。
- 与旧实现的差异：把旧前端散落的 `font-size`/`color` 内联与 `el-icon` 直接使用收敛到 token 尺寸/色；统一 `overdue/near` 业务色图标来源，禁止组件内硬编码色值与任意尺寸。
- 验收断言：Vitest 快照覆盖各 `bb-size`/`bb-color` 与 `bb-label`/`bb-spin`（断言 `aria-hidden` vs `role="img"`+`aria-label`、SVG 类名）；视觉基线断言图标尺寸档与颜色 token 一致、`bb-spin` 动画存在。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；令牌命名全批统一。
