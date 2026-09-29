# 设计契约：BbModal（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一模态对话框，承载确认/表单/警示类中断式交互。
- 封装基线：基于 `el-dialog`。封装原因：旧前端各页自行写弹窗宽高/标题/底栏与遮罩，二次确认风格不一；需统一圆角、阴影、间距、遮罩与 ESC/点遮罩关闭策略，并统一 footer 按钮排布。
- Props 透传约定：透传 `model-value`（`v-model`）、`title`、`width`、`fullscreen`、`top`、`modal`、`modal-class`、`append-to-body`、`append-to`、`lock-scroll`、`close-on-click-modal`、`close-on-press-escape`、`show-close`、`draggable`、`destroy-on-close`、`before-close`。BB 扩展 props：`bb-width`（`sm|md|lg|xl`，默认 `md`，映射固定宽度档，优先于 `width`）、`bb-footer`（Boolean，默认 `true`，控制内置 footer 布局）、`bb-confirm-loading`（Boolean，默认 `false`，确认按钮 loading）。
- Emits / Slots / 方法：透传 `update:modelValue`、`open`、`opened`、`close`、`closed`、`confirm`（BB 扩展，确认按钮点击）；透传 `default`/`header`/`footer` slot（提供 footer 时覆盖内置按钮）；透传 `el-dialog` 方法（`open()`/`close()`），不另造。
- Design Token 映射：
  - 面板：底 `--bb-color-bg-elevated`，边框/分隔 `--bb-color-border`
  - 遮罩：`--bb-color-text-primary` 低透明度（或专用遮罩变量，禁止散写 `rgba(0,0,0,.5)`）
  - 标题文字：`--bb-color-text-primary`（可选 `--bb-font-size-lg`）
  - 正文：`--bb-color-text-secondary`
  - 主/危险操作按钮：`--bb-color-primary` / `--bb-color-danger`
  - 圆角：`--bb-radius-lg`（面板）/ `--bb-radius-md`（按钮）
  - 间距：`--bb-space-5|6`（内边距/顶距）、`--bb-space-4`（footer 按钮间隙）
  - 字号：`--bb-font-size-md|lg`
  - 阴影：面板 `--bb-shadow-lg`
- 状态矩阵：
  - default（open）：面板可见，遮罩生效，焦点移入
  - hover：footer 按钮走 BbButton hover 规则
  - active：footer 按钮走 BbButton active 规则
  - disabled：本组件无整体 disabled；内部表单项禁用态由 BbInput/BbSelect 承载
  - loading：`bb-confirm-loading` → 确认按钮 loading，禁止重复提交，`aria-busy` 于面板
  - closed：`destroy-on-close` 时卸载内容
- 无障碍基准：容器 `role="dialog"`、`aria-modal="true"`，`aria-labelledby` 指向标题、`aria-describedby` 指向正文；打开后**焦点陷阱**在面板内循环，关闭后焦点归还触发元素；Esc 关闭（`close-on-press-escape` 默认开）；遮罩层不可被键盘聚焦；标题/正文对比度 ≥ 4.5:1；`aria-busy` 在 loading 期。
- 与旧实现的差异：收敛旧前端散写的 `width`、`top`、自定义 `.el-dialog` 覆盖样式与各自二次确认实现；统一走 BbModal（配合 BbConfirm）与统一遮罩/圆角/阴影 token，禁止组件内硬编码 `rgba` 遮罩。
- 验收断言：Vitest 快照覆盖 open/closed/footer/loading（断言 `role="dialog"`、`aria-modal`、标题文案、确认按钮 loading）；视觉基线断言面板圆角、阴影、遮罩透明度、footer 按钮间距与 token 一致；交互断言 Esc 关闭、焦点陷阱与焦点归还。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；确认弹窗见 `BbConfirm`；令牌命名全批统一。
