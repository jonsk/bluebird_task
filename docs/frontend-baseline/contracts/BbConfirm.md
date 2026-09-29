# 设计契约：BbConfirm（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §5.3.2 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：为破坏性/关键操作提供统一二次确认（典型用例：**删除任务二次确认**，03 §5.3.2）。
- 封装基线：基于 `ElMessageBox.confirm`
- Props / API 透传约定：
  - 主入口：`BbConfirm.confirm(message, options?): Promise<boolean>`；确认 `resolve(true)`，取消/关闭 `resolve(false)`（内部将 `ElMessageBox` 的 reject 归一为 `false`，调用方无需 try/catch）。
  - 透传 options：`title`、`message`、`confirmButtonText`、`cancelButtonText`、`type`、`confirmButtonClass`、`dangerouslyUseHTMLString`、`distinguishCancelAndClose`、`autofocus`、`showClose`、`center`。
  - 安全默认：`closeOnClickModal = false`、`closeOnPressEscape = true`、`roundButton = true`，防止误触遮罩直接确认。
  - 语义便捷方法：`BbConfirm.danger(message, options?)`（危险主按钮 `--bb-color-danger`）、`BbConfirm.remove(message, options?)`（标准删除文案模板）。
  - 未列出项经 options 透传；禁止业务层直接调用 `ElMessageBox` 散写样式/文案。
- Emits / Slots / 方法：
  - 以 Promise 为交互契约，无 Vue emits；`message` 支持传入 VNode 作为自定义内容。
  - 方法：`confirm/danger/remove` 均返回 `Promise<boolean>`；确认按钮在异步校验期间可切换 loading 文案（经 options 的 `beforeClose`/`confirmButtonText` 组合）。
- Design Token 映射：
  - 危险确认按钮：背景/描边 `--bb-color-danger`，悬停 `--bb-color-danger` 深阶（hover/active 由 token 派生）；取消按钮：文字 `--bb-color-text-primary` + 边框 `--bb-color-border`。
  - 标题：`--bb-color-text-primary`；正文：`--bb-color-text-secondary`；警告图标：`--bb-color-warning` / `--bb-color-danger`。
  - 容器圆角：`--bb-radius-lg`；阴影：`--bb-shadow-lg`；内边距与按钮间距：`--bb-space-4` / `--bb-space-5` / `--bb-space-6`；字号：`--bb-font-size-md`。
- 状态矩阵：`default` 弹窗可见 ｜ `hover` 按钮悬停（danger/取消各自 token） ｜ `active` 按钮按下 ｜ `disabled` 确认按钮禁用（如校验未过） ｜ `loading` 确认按钮加载中（异步提交期间，禁止重复点击并锁关闭）。
- 无障碍基准：
  - 弹窗容器 `role="alertdialog"`、`aria-modal="true"`，并以 `aria-labelledby`/`aria-describedby` 关联标题与正文。
  - 初始焦点落在确认按钮（`autofocus`，可按策略改为取消）；`Enter` 确认、`Esc` 取消；焦点 trap 在弹窗内。
  - 主/次按钮文字与背景对比度 ≥ 4.5:1，危险按钮色不得低于阈值。
- 用例（删除任务二次确认，03 §5.3.2）：`BbConfirm.remove('确认删除该任务？', { message: '删除后不可恢复（软删）' })` → 确认则 `DELETE /tasks/{id}`（软删）并关闭右栏详情抽屉；取消/关闭 → 无副作用；接口失败 → 由请求层统一 `ElMessage` 错误提示，且**不关闭**以允许重试。
- 与旧实现的差异：旧前端用全局 `proxy.$modal`（若依）且文案/危险态不统一；新封装统一 Promise 契约、统一危险语义色与安全默认（遮罩不可误确认），去脚手架依赖。
- 验收断言：
  - Vitest 快照断言 `role="alertdialog"`、标题、正文与按钮文案；断言取消解析为 `false`。
  - 视觉基线（§2.3.2 G7）：结构化断言危险按钮色（`--bb-color-danger`）、圆角与阴影。
  - E2E：断言「取消」不发送 `DELETE`，「确认」发送 `DELETE /tasks/{id}` 并关闭抽屉；失败时弹窗保持打开。
