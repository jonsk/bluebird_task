# 设计契约：BbDrawer（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：全站统一侧边抽屉，承载任务详情等非中断式大面板交互（右栏）。
- 封装基线：基于 `el-drawer`。封装原因：旧 `RightBoxDialog`（984 行巨型组件）自带一套右栏容器样式与开关逻辑，需将「抽屉壳」抽为统一原子组件，统一方位/尺寸/圆角/阴影/关闭策略，供 `BbTaskDetailDrawer` 复用。
- Props 透传约定：透传 `model-value`（`v-model`）、`title`、`direction`（默认 `rtl`）、`size`、`with-header`、`modal`、`append-to-body`、`lock-scroll`、`close-on-click-modal`、`close-on-press-escape`、`show-close`、`destroy-on-close`、`before-close`、`z-index`。BB 扩展 props：`bb-size`（`sm|md|lg`，默认 `md`，映射固定宽度档，优先于 `size`）、`bb-footer`（Boolean，默认 `false`，控制内置 footer 区）。
- Emits / Slots / 方法：透传 `update:modelValue`、`open`、`opened`、`close`、`closed`；透传 `default`/`header`/`footer` slot；透传 `el-drawer` 的 `open()`/`close()`/`handleClose()`。不新增业务方法（业务逻辑由 `BbTaskDetailDrawer` 容器承担）。
- Design Token 映射：
  - 面板：底 `--bb-color-bg-elevated`，边框/分隔 `--bb-color-border`
  - 遮罩：同 BbModal 的统一遮罩变量（禁止散写 `rgba`）
  - 标题：`--bb-color-text-primary`；正文/辅助 `--bb-color-text-secondary`
  - 主/危险操作：`--bb-color-primary` / `--bb-color-danger`
  - 圆角：`--bb-radius-lg`（面板左侧圆角）
  - 间距：`--bb-space-5|6`（内边距/头部分隔）、`--bb-space-4`（区块间距）
  - 字号：`--bb-font-size-md|lg`
  - 阴影：面板 `--bb-shadow-lg`
- 状态矩阵：
  - default（open）：右滑入，面板可见，遮罩生效（可配置 `modal`）
  - hover：内部控件各自态
  - active：内部控件各自态
  - disabled：无整体 disabled；内部表单禁用由原子控件承载
  - loading：本组件不设；内容区加载态由业务容器（如骨架/BbLoading）提供
  - closed：`destroy-on-close` 时卸载内容，释放右栏
- 无障碍基准：容器 `role="dialog"`、`aria-modal` 由 `modal` 决定，`aria-labelledby` 指向标题；打开后焦点移入抽屉、**焦点陷阱**在抽屉内（`modal` 时）；关闭后焦点归还触发卡片；Esc 关闭（默认开）；`with-header` 为否时调用方须自行提供可访问标题；标题/正文对比度 ≥ 4.5:1。
- 与旧实现的差异：把旧 `RightBoxDialog` 内嵌的右栏容器样式、宽度与开关状态机收敛为 BbDrawer 原子壳，业务详情内容拆入 `BbTaskDetailDrawer`（+子组件/composable），消除 984 行巨型组件的容器耦合与散写遮罩/宽度。
- 验收断言：Vitest 快照覆盖 open/closed/header 可见性/`bb-size` 档（断言 `role="dialog"`、`direction`、标题文案、宽度档）；视觉基线断言面板宽度档、圆角、阴影与 token 一致；交互断言 Esc 关闭、焦点陷阱与关闭后焦点归还。

---
> 关联：设计令牌见 03 §4.4；原子组件总表见 03 §6；业务容器 `BbTaskDetailDrawer` 见同名契约；令牌命名全批统一。
