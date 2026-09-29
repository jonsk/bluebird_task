# 设计契约：BbTag（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一标签/徽标，用于任务标签（含「紧急/重要」语义着色）、状态与计数展示。
- 封装基线：基于 `el-tag`。封装原因：旧 `TaskCard.vue` 的标签着色规则以字符串匹配「紧急→红、重要→蓝」硬编码散落在卡片内（见 03 §2.3.1），需把业务语义色收敛为 `bb-intent` 与统一 token，供 `BbTaskMetaLine`/`BbTagConfig` 复用。
- Props 透传约定：透传 `type`（`primary|success|warning|danger|info`）、`size`、`effect`（`dark|light|plain`）、`round`、`closable`、`disable-transitions`、`hit`。BB 扩展 props：`bb-intent`（`default|urgent|important|success|warning|danger|overdue|near`，默认 `default`；`urgent`→`--bb-color-danger`、`important`→`--bb-color-primary`、`overdue`→`--bb-color-overdue`、`near`→`--bb-color-near`）、`bb-size`（`sm|md|lg`，默认 `md`）、`bb-text`（可选，替代默认 slot 的纯文本快捷方式）。
- Emits / Slots / 方法：透传 `close`（可关闭标签）；透传默认 slot 与 `prefix` slot（如有）；无专属方法。
- Design Token 映射：
  - 语义/业务色：`--bb-color-danger`（urgent）、`--bb-color-primary`（important）、`--bb-color-success`、`--bb-color-warning`、`--bb-color-overdue`、`--bb-color-near`
  - 文本：标签文字 `--bb-color-text-primary`（`light` 态用语义色前景）；面底色可用 `--bb-color-bg-elevated` 或语义色浅底
  - 边框：`--bb-color-border`（`plain` 态用语义色边）
  - 圆角：`--bb-radius-sm` / `--bb-radius-md`（round 用 `--bb-radius-lg`）
  - 间距：`--bb-space-1|2`（内边距/间隙）
  - 字号：`--bb-font-size-sm|md|lg`
  - 阴影：`--bb-shadow-sm`（可选，`dark`/`hit` 态）
- 状态矩阵：
  - default：语义/默认底色，文字可读
  - hover：`hit`/`closable` 时底色弱提升或显示关闭图标
  - active：点击类标签的按下态（业务按需，非必设）
  - disabled：本组件无 disabled；只读场景不绑 `closable` 即不可交互
  - loading：不适用（标签为静态展示，无 loading 态）
  - 可关闭：`closable` 时显示关闭按钮且键盘可达
- 无障碍基准：纯展示用 `<span>` 语义，颜色不得作为唯一信息载体（如「紧急」须同时有文字）；`closable` 时关闭按钮 `aria-label`（如「移除<标签名>」）且 `Tab` 可达；文字与底色对比度 ≥ 4.5:1（`light`/`plain` 态须校验）。
- 与旧实现的差异：把旧 `TaskCard.vue` 内联的「含『紧急』红 / 含『重要』蓝」硬编码判断收敛到 `bb-intent` 映射与 token；标签样式统一，禁止各业务组件自行写标签色值。
- 验收断言：Vitest 快照覆盖各 `bb-intent` 与 `effect` 组合、`closable` 关闭事件（断言类名/文案/`aria-label`）；视觉基线断言「紧急」= `--bb-color-danger`、「重要」= `--bb-color-primary`、`overdue`/`near` 与 token 一致，文字对比达标。

---
> 关联：设计令牌见 03 §4.4；标签着色业务规则见 03 §2.3.1（`TaskCard` 契约）；令牌命名全批统一。
