# 设计契约：BbInput（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一文本输入框（含多行文本域），承载表单录入与统一校验/态样式。
- 封装基线：基于 `el-input`。封装原因：旧前端输入框边框/内边距/聚焦色不一致、尺寸靠逐处 Tailwind 类拼接，需统一 token 尺寸、focus 态与 `el-form` 校验错误色（`--bb-color-danger`）。
- Props 透传约定：透传 `model-value`（`v-model`）、`type`（含 `textarea`）、`size`、`placeholder`、`clearable`、`show-password`、`disabled`、`readonly`、`maxlength`、`minlength`、`show-word-limit`、`autosize`、`rows`、`prefix-icon`、`suffix-icon`、`validate-event`、`formatter`/`parser`。BB 扩展 props：`bb-size`（`sm|md|lg`，默认 `md`，映射 `--bb-space-*` 高度与 `--bb-font-size-*`）、`bb-status`（`default|success|warning|error`，默认 `default`，用于内联态着色的业务场景）。
- Emits / Slots / 方法：透传 `update:modelValue`、`input`、`change`、`focus`、`blur`、`clear`、`keydown`（按 Enter 等）；透传 `prefix`/`suffix`/`prepend`/`append` slot；透传 `focus()`/`blur()`/`select()`/`clear()`/`textarea` 相关方法。
- Design Token 映射：
  - 边框：默认 `--bb-color-border`；focus → `--bb-color-primary`；error → `--bb-color-danger`；success → `--bb-color-success`；warning → `--bb-color-warning`
  - 底色：`--bb-color-bg`；禁用底 `--bb-color-bg-elevated`
  - 文本：输入 `--bb-color-text-primary`；placeholder/辅助 `--bb-color-text-secondary`；禁用 `--bb-color-text-disabled`
  - 圆角：`--bb-radius-md`（默认）/ `--bb-radius-sm`（sm）
  - 间距：`--bb-space-2|3`（内边距）、`--bb-space-2`（图标间隙）
  - 字号：`--bb-font-size-sm|md|lg`
  - 阴影：focus 可选 `--bb-shadow-sm`
- 状态矩阵：
  - default：`--bb-color-border` 1px，`--bb-color-bg`
  - hover：边框 → `--bb-color-text-secondary`（弱提升）
  - focus/active：边框 → `--bb-color-primary`，focus ring `outline:2px solid var(--bb-color-primary)`（或等价 box-shadow）
  - disabled：底 `--bb-color-bg-elevated`、文字 `--bb-color-text-disabled`、`cursor:not-allowed`、不可聚焦
  - loading：本组件不设 loading 态；占用可经 `suffix-icon`/外部 spinner（明确不封装 loading）
  - 校验态：`error` 用 `--bb-color-danger`，与 `el-form` 校验联动
- 无障碍基准：根 `<input>`/`<textarea>` 原生语义；`label`/`aria-label` 必填（由 `el-form-item` 或调用方提供）；placeholder 不可替代 label；focus ring 可见且对比达标；错误态关联 `aria-invalid="true"` 与 `aria-describedby` 指向错误信息；正文文字与底色对比度 ≥ 4.5:1。
- 与旧实现的差异：收敛旧前端逐处硬编码 `border:1px solid #dcdfe6`、聚焦色与 `padding` 内联样式；错误态统一 `--bb-color-danger`，不再各自写红；placeholder 统一 `--bb-color-text-secondary`。
- 验收断言：Vitest 快照覆盖 default/focus/disabled/error 四态（断言 `aria-invalid`、`disabled`、类名与文案）；视觉基线断言边框色、圆角、字号、focus ring 与 token 一致；表单校验失败时错误文案与红边同时出现。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；令牌命名全批统一。
