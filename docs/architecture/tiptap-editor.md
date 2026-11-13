# Tiptap 编辑器：扩展、补丁与外围管线

> 适用版本：`@tiptap/* 3.33.1`（`frontend/package.json`）
> 相关入口：`frontend/src/components/note/NoteEditor.vue`、`NoteContent.vue`、`frontend/src/components/weekly/WeeklyReportEditor.vue`

本文梳理我们在 Tiptap 之上做的所有事情。原则只有一条：**不 fork、不改 `node_modules`**，全部通过扩展层（`Node.create` / `Extension.create` / `.extend()` / 配置项）实现，升级 Tiptap 时只需回归本文列出的点。

---

## 1. 整体结构

```
buildNoteExtensions(options)            // utils/noteEditorExtensions.ts —— 唯一的扩展装配入口
├── 上游扩展（按项目需要配置）
├── 上游补丁（1 处，见 §2；已移除的补丁见 §9）
├── 自建扩展（6 个，见 §3）
└── 按 mode / variant 裁剪
```

`buildNoteExtensions(options)` 的三个开关：

| 选项 | 取值 | 作用 |
| --- | --- | --- |
| `mode` | `editor` \| `render` | `editor` 额外注册斜杠菜单、链接点击、占位符；`render` 用于详情页只读渲染（周报虽走 `editor`，但仍裁掉斜杠菜单与链接点击） |
| `variant` | `note` \| `weekly` | `weekly` 裁掉依赖 `noteId` 的节点：附件、图片、斜杠菜单、链接点击 |
| `placeholder` | 字符串 | 编辑态占位文案 |

**关键约束：编辑态、只读态、周报必须共用同一套 schema。** 否则详情页会把编辑器里合法的节点（折叠块、待办、颜色、附件）当成未知标签丢掉，同一篇日志在编辑态与详情态排版不一致。周报复用日志的 schema，只做节点裁剪（`utils/weeklyReportExtensions.ts`）。

装配清单（`utils/noteEditorExtensions.ts`）：

| 扩展 | 配置要点 |
| --- | --- |
| `StarterKit` | 标题只保留 1–3 级；`code`/`codeBlock` 关闭且不保留对应节点（旧的 `<code>`/`<pre>` 会降级为普通文本）；`link.openOnClick` 仅在只读渲染态（非周报）开启 |
| `Highlight` | `multicolor: true` |
| `TextStyle` + `Color` | 颜色 mark |
| `TaskList` + `NoteTaskItem` | `nested: true`（见 §2.1） |
| `Details` + `DetailsSummary` + `DetailsContent` | `persist: true`、`openClassName: 'is-open'`、自定义折叠按钮 `details-toggle` + `aria-label` 收起/展开；`DetailsSummary` 必须显式注册（`Details` 的 schema 依赖 `detailsSummary` 节点类型），标题沿用上游 `text*`，只接受纯文本 |
| `Image` | `inline: false`、`allowBase64: false`、`class: note-image`、`resize` 开启（四角手柄、最小 64×64、等比缩放，见 §7 第 5 条）；周报不注册 |
| `TableKit` | `resizable`、`renderWrapper`、`class: note-table` |
| `TextAlign` | 作用于 `heading` / `paragraph` |
| `NoteTypography` / `NoteIndent` | 块级排版与缩进（见 §3.3、§3.4） |
| `NoteAttachment` | 依赖 `noteId`，周报不注册 |
| `NoteEmoji` | 两种编辑器都启用 |
| `TableOfContents` | 自定义 `getId`：中文保留汉字，生成 `toc-{slug}` 锚点，配合 `NoteOutline` |
| `NoteLinkClick` / `SlashCommand` / `Placeholder` | 仅 `mode: 'editor'`；前两者对周报再裁剪 |

---

## 2. 上游补丁

> 当前只剩一处补丁。已经删除的补丁（`LegacyCode` / `LegacyCodeBlock`、`stateTick`、`NoteDetailsSummary`）统一记录在 §9，避免重新加回来。

### 2.1 `NoteTaskItem`：待办项不再创建 a11y 隐藏 span

**文件**：`frontend/src/utils/noteTaskItem.ts`　**测试**：`frontend/tests/unit/noteTaskItem.spec.ts`

**症状**：在待办清单里输入中文标点（如 `。`）后，光标视觉上左移一格；连续输入累积成落后 2、4、6 格。普通段落、有序/无序列表均正常。

**根因**：Tiptap 3.33 的 TaskItem NodeView 在每次 `update` 时执行

```js
checkbox.setAttribute('aria-label', label)
checkboxStyler.textContent = label   // 隐藏 span，每次更新整体重写文本节点
```

`textContent` 赋值在编辑器 DOM 内部等价于“删除旧文本节点 + 插入新文本节点”。Chromium 在该结构变更后恢复选区会退一格；组合输入（中文标点走 IME composition）时最明显。属性写入（`data-checked`、`aria-label`）本身无害。

**修法**：扩展 `TaskItem`，包一层父工厂，在 NodeView 创建、`dom` **尚未插入文档**的阶段摘掉 `label > span`：

```ts
export const NoteTaskItem = TaskItem.extend({
  addNodeView() {
    const parentFactory = this.parent?.()
    if (!parentFactory) return null
    return (props) => {
      const nodeView = parentFactory(props)
      nodeView.dom?.querySelector('label > span')?.remove()
      return nodeView
    }
  },
})
```

要点：

- 必须是**游离阶段**移除。在文档内直接删除会被 ProseMirror 的 DOMObserver 捕获并还原；反复增删还会导致重绘死循环、页面卡死。
- 无障碍名称仍由 `checkbox` 的 `aria-label` 提供，上游 `updateA11Y` 继续维护；屏幕阅读器不受影响。
- 上游 3.33.1 仍未修复该行代码，升级后需复查；若上游改为“值不变则不写”，本补丁可删。

**验证脚本思路**（浏览器控制台，定位同类问题）：在 `.ProseMirror` 上挂 `MutationObserver`，过滤 `li[data-checked]`，观察每次按键的变更组合。修复前每键固定出现 `characterData:#text` → `attributes:LI(data-checked)` → `attributes:INPUT(aria-label)` → `childList:SPAN +1 -1`；修复后 `SPAN` 相关变更消失，光标正常。

### 2.2 配置覆盖（不改源码）

- 标题只保留 1–3 级，避免层级过深导致大纲/排版失控；
- `link.openOnClick` 只在只读渲染态（非周报）开启——编辑态普通点击必须只用于定位光标，见 §3.6。

**已知差异**：日志编辑器支持 `setEditable(false)`（权限只读），此时 `mode` 仍是 `'editor'`，`openOnClick` 为 `false`，只读编辑器里点链接不会跳转。若希望“只要不可编辑就能点开”，可改为 Tiptap 支持的字符串形式：

```ts
openOnClick: isWeekly ? false : 'whenNotEditable'
```

---

## 3. 自建扩展

### 3.1 `NoteEmoji` — 行内 Emoji 节点

**文件**：`frontend/src/utils/noteEmoji.ts`

- 节点名 `emoji`，`group: 'inline'`，`atom: true`；
- 属性 `name`，解析 `span[data-type="emoji"]`，渲染带 `class="note-emoji"`；
- `renderText` 输出真实 emoji 字符，保证导出纯文本时不出现 `:name:`；
- 命令 `setEmoji(name)`；
- 输入规则：`:smile:` 形式；
- 交互：输入 `:` 触发 suggestion 菜单（`@tiptap/suggestion` + `VueRenderer` + tippy，组件 `NoteEmojiMenu.vue`），按名称/中文名/短码/标签检索。

### 3.2 `NoteAttachment` — 非图片附件卡片

**文件**：`frontend/src/utils/noteAttachment.ts`，节点视图 `NoteAttachmentNode.vue`

- `group: 'block'`、`atom: true`、`draggable`、`selectable`；
- 只保存渲染所需最小字段：`attachmentId`、`fileId`、`fileName`、`size`、`contentType`、`downloadUrl`、`previewUrl`；
- 权限与引用保护由后端 `note_attachment_ref` 判定，前端不缓存附件实体，避免打开日志时再查一次附件表；
- 使用 `VueNodeViewRenderer`；节点自身 `draggable: true`，编辑器级块拖拽手柄见 §4.6。

### 3.3 `NoteTypography` — 块级字体/字号/行距

**文件**：`frontend/src/utils/noteTypography.ts`

- 用全局属性挂在 block（`paragraph` / `heading`）上，而不是 TextStyle mark：三项设置通常作用于整段，块级属性在没有选区时也能生效，并且能完整写进 HTML `style`；
- 属性 `fontFamily` / `fontSize` / `lineHeight`，从 `element.style` 解析、渲染回 `style`；
- **值白名单**：`fontSize` 必须带单位，`lineHeight` 允许无单位数字，任何值含 `;`、`<`、`>` 一律丢弃，防止任意 CSS 注入。

### 3.4 `NoteIndent` — 缩进

**文件**：`frontend/src/utils/noteIndent.ts`

- 段落/标题使用 `data-indent` 属性（0–6），CSS 统一换算缩进宽度，避免和多行缩进、`text-align`、字号打架；
- 命令 `indentBlock` / `outdentBlock`：
  - 在 `taskItem` / `listItem` 内自动转成 `sinkListItem` / `liftListItem`（列表缩进 = 嵌套层级）；
  - 其余情况递增/递减 `indent`，并夹在 `[0, max]`。
- 快捷键 `Tab` / `Shift-Tab`（`addKeyboardShortcuts`）：
  - 只接管段落与标题；到 0 / 6 边界仍拦截默认行为，避免焦点被 Tab 带出编辑器；
  - **表格单元格内直接 `return false`**：Tab 是“下一个单元格”，交给 `TableKit`；
  - **列表项内直接 `return false`**：Tab / Shift-Tab 是 `sinkListItem` / `liftListItem`，交给上游 `ListKeymap`；
  - 图片/附件/折叠块标题等非段落块不拦截，保留浏览器默认行为。

### 3.5 `SlashCommand` — 斜杠菜单

**文件**：`frontend/src/utils/slashCommand.ts`，菜单组件 `SlashMenu.vue`

- `/` 触发的 suggestion 菜单；
- 条目 `SlashItem` 含标题、描述、图标、分组（基础/结构/媒体等）、关键字，可按中英文检索；
- 每个条目自带 `command({ editor, range })`，统一先 `deleteRange(range)` 再执行目标命令；
- 仅编辑态注册（`mode: 'editor'`），周报不注册。

### 3.6 `NoteLinkClick` — Ctrl/Cmd + 单击打开链接

**文件**：`frontend/src/utils/noteLinkClick.ts`

编辑态关闭了 `openOnClick`（普通点击用于定位光标）。这里用 ProseMirror 插件接管 `handleClick`：

- 只处理 `metaKey || ctrlKey`；
- 只处理编辑器内 `<a>`，拒绝空 `href` 与 `javascript:`；
- `window.open(href, '_blank', 'noopener,noreferrer')`；
- 周报不注册（`buildNoteExtensions` 的 `isWeekly` 分支），因此周报里的链接不响应 Ctrl/Cmd 单击，普通点击也只定位光标。

---

## 4. 外围管线

### 4.1 剪贴板：Excel / WPS 表格

**文件**：`frontend/src/utils/excelTablePaste.ts`，接入点 `NoteEditor.vue` 的 `handlePaste`

- Excel 复制区域时，剪贴板同时有 `text/html`（带 `<table>`）、`text/plain`（TSV）和一张区域 PNG 快照；
- **只在“整段内容就是一张表”时接管**（`isHtmlOnlyTable`）：`body` 里恰好一张 `<table>`，表格之外没有非空文本、图片或其它块级元素；`<meta>`、注释、Office 命名空间、空的包裹层（`div.tableWrapper`）不算内容；
- 正文里夹表格的整篇粘贴、多表文档一律 `return false`，交给 ProseMirror 原生解析——否则转换器只取第一张表，其余内容全部丢失；
- TSV 兜底**只在没有 `text/html` 时启用**，避免把带制表符的代码误判成表格；
- 接管时把 Excel 的 `colgroup`、内联样式、命名空间转成 Tiptap JSON，保留 `colspan` / `rowspan`（缺字段会被 schema 拒绝），合并单元格按占用矩阵补齐行宽。

### 4.2 粘贴 / 拖拽上传

`NoteEditor.vue` 的 `handlePaste` / `handleDrop`：

- 图片走 `uploadImages`，普通文件走 `uploadAttachments`；
- 剪贴板里同时带 HTML 表格（但没被 §4.1 的“仅表格”分支接管）时直接 `return false`：交回原生解析，避免 Excel 附带的区域快照被当图片上传、整段内容变成一张截图；
- 两类文件都命中时先 `preventDefault` 再分别上传，避免浏览器默认插入行为。

### 4.3 正文图片鉴权水合

**文件**：`frontend/src/utils/markdownImages.ts`，接入点 `NoteEditor.vue` 的 `attachHydrator` / `NoteContent.vue`

- 附件端点需要 `Authorization: Bearer`，而 `<img src>` 不会带请求头；
- 用 axios 取 blob → `URL.createObjectURL` → 写回 `src`，与附件下载/预览走同一条鉴权链路，不引入签名 URL 或匿名白名单；
- 只处理日志附件预览地址，外链图片原样保留；
- 两个不变量：`stop()` 后页面上不能残留未 revoke 的 blob URL 的 `<img>`（否则 KeepAlive/v-show 复用后永远裂图）；父级重绘或 `setContent` 把 `src` 写回原地址时必须重新水合（因此既要观察子节点新增，也要观察 `src` 属性变化）；
- **容器可能晚于首帧才挂载**（编辑态加载遮罩 `v-if`、异步组件、请求返回后才有正文）：`attach()` 在 root 为空时用 `requestAnimationFrame` 继续等待（`waitForRoot`），不能在首次 attach 失败后放弃；
- **容器元素会被整体替换**（例如 `NoteEditor` 的加载态销毁并重建 `.note-editor__body`）：水合器不能只在 `onMounted` 绑一次，`NoteEditor` 用 `attachHydrator()`（先 `stop()` 旧实例再重建）配合 `watch(hostRef)` 跟随容器变化；`NoteContent` 在 `apply()` 后 `nextTick` 重建，两条路径保持一致；
- **图片 resize 会重写 `src`**：上游 `ResizableNodeView` 在 resize 提交时按节点属性把 `src` 写回预览地址。水合器缓存「预览地址 → objectURL」，同一地址再次出现时直接复用、不重复请求（相关配置见 §3 的 `Image` 与 §7 第 5 条）；`stop()` 时统一 revoke 并清空缓存。

### 4.4 HTML 正文与纯文本工具

**文件**：`frontend/src/utils/richText.ts`

- 正文一律是 Tiptap `getHTML()` 产出的 HTML（历史 Markdown 兼容层已移除，见 §9.4）；
- `toEditorHtml`：空内容落成 `<p></p>`，其余一律过 `sanitizeRichTextHtml`，与后端 `RichTextGuard` 组成双防线；
- `plainTextFromHtml` / `isBlankHtml` / `visibleLength`：保存校验与字数统计；
- `plainSummary`：列表卡片 / 右栏小列表 / 详情页标题的单行摘要。

### 4.5 HTML 清洗

**文件**：`frontend/src/utils/sanitize.ts`

- `sanitizeRichTextHtml`：AI 生成或直连 API 写入的内容统一清洗；
- `containsDangerousRichText`：拒绝危险片段；
- 规则要点：禁用 `srcdoc`/`formaction`/`ping`/`attributionsrc`/`xmlns` 等属性；`href`/`src` 等 URL 属性校验协议；`<input>` 只允许 `type/checked/disabled`；`style` 命中危险 CSS 则移除；历史数据里的 base64 位图单独处理（Image 关闭了 `allowBase64`）。

### 4.6 块拖拽手柄

**上游**：`@tiptap/extension-drag-handle-vue-3`　**公共逻辑**：`frontend/src/composables/useBlockDragHandle.ts`

- 上游 `DragHandle` 负责跟随 hover 的块、把浮层定位到块左侧，并提供拖拽排序（`compute-position-config` 为 `left-start`）；
- 浮层内是本项目自己的两个按钮（`NoteEditor.vue` / `WeeklyReportEditor.vue`）：`+` 在当前块后插入空段落，`⠿` 用于拖拽；
- `useBlockDragHandle` 只承担两部分：`onNodeChange` 记录 hover 块位置（`{ pos, size }`），`insertBelow` 在 `pos + size` 处插入空段落并把光标放进去（没有 hover 块时退回选区末端）；
- 仅编辑态渲染（`NoteEditor` 用 `canEditContent`，周报用 `!readonly`），只读详情页没有手柄；日志与周报共用，避免两处边界处理分叉。

---

## 5. 维护准则

1. **schema 双端一致**：新增节点/属性时必须同时考虑 `mode: 'editor'`、`mode: 'render'` 与 `variant: 'weekly'`，否则只读渲染会丢内容。
2. **依赖 `noteId` 的节点放 `variant !== 'weekly'` 分支**：附件、图片、斜杠菜单、链接点击。
3. **不要修改 `node_modules`**：一切通过 `.extend()` / 配置项实现；上游缺陷用“创建期摘除”的方式绕，不要在文档内删除节点（会触发 DOMObserver 重绘甚至死循环）。
4. **状态同步用 Tiptap Vue 的响应式 `state` / `storage`**：`computed` 或模板里现场调用 `editor.isActive` / `getAttributes`；不要自建 tick，也不要读 `editor.view.state`。
5. **一切来自用户或外部的内容先清洗**：HTML 走 `sanitizeRichTextHtml`，CSS 值走白名单正则。
6. **只读渲染也走同一扩展集**：`NoteContent.vue` 用 `mode: 'render'`，不要为渲染另写一套 HTML 处理。
7. **新增交互优先用命令封装**（`addCommands`），工具条、斜杠菜单、快捷键共用同一个命令，避免行为分叉。

---

## 6. 验证与回归

自动化：

```bash
cd frontend
pnpm exec vue-tsc --noEmit
pnpm exec eslint src tests --ext .ts,.vue --max-warnings 0
pnpm exec vitest run
```

手动回归清单：

- 待办项：行末 / 句中 / 连续输入 `。`，光标不左移、不累积；新建待办（`- [ ]`）、嵌套、Tab / Shift-Tab、勾选与取消、只读态；
- 整篇粘贴：全选文档（标题 / 段落 / 有序列表 / 待办 / 表格）粘到新记录，内容完整且表格可编辑；Excel 区域仍粘成表格而不是截图；
- 工具栏 / 气泡菜单 / 大纲：选中或移动光标后按下态、块类型、对齐、字体字号行距、大纲高亮是否跟随（不再依赖 `stateTick`）；日志编辑态能看到图片 / 附件入口（`NoteToolbar` 的 `attachments` 默认 `true`，周报传 `false`）；
- 折叠块：新建 / 展开 / 收起、`open` 状态持久化；标题只接受纯文本，粘贴含 Emoji 的文本时表情被过滤、文字保留；
- 斜杠菜单：各分组条目可插入；
- Emoji：`:smile:` 与菜单两条路径，导出纯文本正确；
- 附件：上传、拖拽、卡片下载；
- 缩进：段落/标题 Tab / Shift-Tab 增减缩进（0–6），列表内 Tab 仍是嵌套、不写 `data-indent`；
- 表格：Excel 复制粘贴（含合并单元格）、列宽拖拽、单元格内 Tab 切换单元格（不被缩进拦截）；
- 图片：粘贴上传、正文预览（带鉴权）、加载态结束 / 切换编辑与只读后仍能水合、四角拖拽改尺寸后不重复请求且尺寸写入 HTML、离开页面后无残留 blob URL；
- 历史日志：打开、编辑、保存均按 HTML 处理（Markdown 兼容层已移除，见 §9.4；代码块此前已随 §9.1 降级）；
- 周报：不出现附件/图片/斜杠菜单入口，工具栏状态同步正常；正文里的链接不响应 Ctrl/Cmd 单击。

---

## 7. 升级 Tiptap 时先看这几处

1. `@tiptap/extension-list` 的 `task-item` 源码：`updateA11Y` 是否仍每次 `textContent` 重写（若是，`NoteTaskItem` 保留）；
2. `Suggestion` 的 `render` 回调签名与 `VueRenderer` 用法（Emoji 与斜杠菜单共用）；
3. `TableKit` 的 `renderWrapper` 与 `resizable` 选项；
4. `TableOfContents` 的 `getId` 行为（版本升级可能改变默认锚点生成）；
5. `Image` 的 `resize` 选项与 `ResizableNodeView`（3.33 起上游内置；启用后 resize 提交会按节点 attrs 重写 `src`，需同步检查 §4.3 的水合逻辑）。

升级后跑一遍 §6，重点复测待办项光标、Excel 粘贴两类。

---

## 8. 文件索引

| 文件 | 作用 |
| --- | --- |
| `utils/noteEditorExtensions.ts` | 扩展总装配（唯一入口） |
| `utils/weeklyReportExtensions.ts` | 周报变体 |
| `utils/noteTaskItem.ts` | TaskItem 补丁（§2.1） |
| `utils/noteEmoji.ts` / `noteEmojis.ts` | Emoji 节点与数据 |
| `utils/noteAttachment.ts` | 附件卡片节点 |
| `utils/noteTypography.ts` | 块级排版属性 |
| `utils/noteIndent.ts` | 缩进 |
| `utils/slashCommand.ts` | 斜杠菜单 |
| `utils/noteLinkClick.ts` | Ctrl/Cmd 点击链接 |
| `utils/excelTablePaste.ts` | Excel 表格粘贴（含“仅一张表才接管”的判定） |
| `utils/markdownImages.ts` | 正文图片鉴权水合 |
| `utils/richText.ts` | HTML 消毒包装、纯文本化与摘要 |
| `utils/sanitize.ts` | HTML 清洗 |
| `composables/useBlockDragHandle.ts` | 块拖拽手柄逻辑 |
| `components/note/NoteEditor.vue` | 编辑态装配（粘贴/拖放；状态同步交给 Tiptap 响应式 `state`） |
| `components/note/NoteContent.vue` | 只读渲染装配 |
| `components/weekly/WeeklyReportEditor.vue` | 周报装配 |

---

## 9. 已移除的补丁与功能

这一章专门记录**曾经存在、后来删除**的补丁与功能。目的不是考古，而是防止两件事：

- 后来者看到历史注释或旧文档，把已经删掉的逻辑重新加回来；
- 再次遇到相似问题时，重复已经验证过的排查过程。

每条都写明：当初为什么加、为什么能删、删掉后的实际行为、以及是否留下已知缺口。

### 9.1 `LegacyCode` / `LegacyCodeBlock`（旧代码节点）

- **原文件**：`frontend/src/utils/noteLegacyCode.ts`（已删除），附测试 `frontend/tests/unit/noteLegacyCode.spec.ts`（已删除）。
- **当初为什么加**：日志编辑器从 Vditor（Markdown）迁到 Tiptap 后，库里同时存在两类历史内容——Markdown 经 `marked` 转出的 `<pre><code class="language-x">`，以及精简代码入口前用工具栏保存的 HTML `<code>` / `<pre>`。只关闭 `StarterKit` 的 `code` / `codeBlock` 而不保留节点，旧内容重新保存时标签会被当未知元素剥掉、格式永久丢失。因此保留了两个“只解析、不暴露入口”的节点。
- **为什么能删**：确认不再需要兼容这些历史数据；且节点不提供任何创建入口（工具栏、斜杠菜单、输入规则、快捷键都已关闭），保留成本虽低但只剩负担。
- **删除后的实际行为**（jsdom 实测）：
  - `<code>` → 普通文本，等宽样式消失；
  - `<pre><code class="language-x">` → 一个普通段落 + 若干硬换行，缩进空格保留，语言标记丢失；
  - 打开不触发保存，但**任何一次编辑后的自动保存都会把降级结果写回库**，不可恢复。
- **已知缺口**：历史 Markdown 里的 `<code>` / `<pre>` 同样会被降级；Markdown 兼容层现已一并移除（§9.4），旧内容按 HTML 处理，不再有转换成节点的路径。这是有意接受的取舍，不是 bug。

### 9.2 `stateTick`（手写 UI 状态同步）

- **原位置**：`components/note/NoteEditor.vue`、`components/weekly/WeeklyReportEditor.vue`，以及三个消费方 `NoteToolbar` / `NoteOutline` / `NoteBubbleMenu` 的 `tick` prop。
- **当初为什么加**：Tiptap 3 的 Vue 集成早期被误认为不会让 `editor.state` 触发组件更新，于是用“每个事务递增 `tick`”强制工具栏、大纲、气泡菜单重算 `isActive` / 块级属性 / 大纲项。日志编辑器还额外加了“组合输入期间跳过 + 单帧 `requestAnimationFrame` 合并”的节流，最初是为了排查“中文标点后光标左移”。
- **为什么能删**：`@tiptap/vue-3` 3.33 本身就把这件事做在框架里：

  ```js
  this.reactiveState = useDebouncedRef(this.view.state)
  this.on('beforeTransaction', ({ nextState }) => { this.reactiveState.value = nextState })
  get state() { this.reactiveState?.value; return super.state }
  ```

  `state`、`storage` 都通过 `customRef` 的 `track()` 建立依赖，事务后经两次 `requestAnimationFrame` 触发重算；`editor.isActive()` / `editor.getAttributes()` 内部读的就是 `this.state`。因此只要组件在 `computed` 或模板里**现场调用**这些 API，就会自动更新，不需要外部信号。
- **删除后新增的回归测试**：`frontend/tests/unit/NoteToolbar.spec.ts` 里的 `NoteToolbar 状态同步` 用例——用真实 `Editor` + `StarterKit` 挂载工具栏，`selectAll + toggleBold` 后断言按钮按下态出现/消失，不传任何 `tick`。
- **使用自动同步的三条约束**（踩过就别再踩）：
  1. 不要在 `setup` / `onMounted` 里取一次值存成 `ref`——一次性快照不会更新；
  2. 不要读 `editor.view.state`（ProseMirror 原始对象，不参与 Vue 追踪），改用 `editor.state`；
  3. 依赖 `editor.storage`（如大纲）时，保留那次读取即可，无需额外信号。
- **顺带澄清**：这条节流当初被认为与“光标左移”有关，实际根因是 §2.1 的 TaskItem 隐藏 span。删除它不影响光标修复。

### 9.3 `NoteDetailsSummary`（折叠块标题内容模型）

- **原位置**：`frontend/src/utils/noteEditorExtensions.ts`，`DetailsSummary.extend({ content: 'inline*' })`，与 `Details` / `DetailsContent` 一起注册。
- **当时为什么加**：上游 `detailsSummary` 的 `content` 是 `text*`，只允许文本节点；而 Emoji 是 inline atom、提及是 inline node，往折叠块标题里插入会被 schema 拒绝（命令静默返回 `false`）。放宽为 `inline*` 后，标题可以放 Emoji、提及、硬换行。
- **为什么删**：产品上没有在折叠块标题里使用 Emoji / 提及的场景，收益低于维护成本；退掉后与上游 schema 完全一致，升级时少一个复查点。
- **删除后的实际行为**（与 §6 回归项一致）：
  - 标题只剩文本与文本 mark（粗体 / 颜色 / 高亮仍可用）；
  - 在标题里输入 `:smile:`：输入规则被 schema 拒绝，表现为“点了没反应”；`@` 现在只是普通字符（提及节点已整体移除，见 §9.5）；
  - 从外部粘贴含 Emoji 的文本：文字保留，Emoji 节点被过滤；
  - 硬换行被丢弃或合并；
  - 历史数据里标题内的 Emoji 在打开时即被过滤，**编辑一次后保存会永久丢失**；提及节点移除后，其中的 `@姓名` 文字按纯文本保留。
- **已知缺口**：历史上标题内 Emoji 的存量未统计（如需评估，用 HTML 中 `<summary>` 内是否含 `data-type="emoji"` 做摸底）；若确认有量且必须保留，只能恢复补丁或做一次性内容迁移。
- **不要因为“升级后标题里 Emoji 插不进去”就重新加回**：这是上游默认行为，属于已确认取舍。

### 9.4 历史 Markdown 兼容（前端 + 后端）

- **原位置**：
  - 前端 `frontend/src/utils/richText.ts`：`isHtmlContent` 格式判别、`markdownToHtml`（`marked`）、`normalizeTaskLists`，以及 `toEditorHtml` 的双分支；
  - 前端 `frontend/src/utils/markdown.ts`：`stripMarkdown`（列表卡片 / 小列表 / 详情页标题的 Markdown 去符号）；
  - 后端 `MarkdownText`（现改名 `RichTextPlain`）：`looksLikeHtml`、`HTML_START`、`plainMarkdown` 分支；
  - 依赖：`frontend/package.json` 的 `marked`。
- **当初为什么加**：日志编辑器从 Vditor（Markdown）迁到 Tiptap 后，库里遗留了 Markdown 源码。用“是否以块级标签开头”做轻量判别，不需要新增数据库字段；旧日志在打开/编辑时转成 HTML，用户编辑后自动保存即完成迁移。
- **为什么删**：确认不需要兼容历史 Markdown；判别逻辑把“存储格式”变成了隐式约定，前端与后端各维护一套，规则容易漂移。
- **删除后的实际行为**：
  - 所有正文一律按 HTML 处理，只过 `sanitizeRichTextHtml` / `RichTextGuard`；
  - 若库里仍有 Markdown 源码，会作为纯文本进入编辑器：整篇合并为一个段落，`##`、`**`、`- [ ]`、表格管道符、`[链接](url)` 等原样可见；**编辑一次后保存即固化，不可恢复**；
  - 摘要链路：前端 `plainSummary`、后端 `RichTextPlain.plain` 都只剥 HTML 标签，不再去 Markdown 符号；
  - 任务列表、代码块、表格等旧 Markdown 结构不再有转换入口。
- **已知缺口**：删除前未做存量摸底。若事后发现历史日志大面积变乱，只能从备份恢复，或写一次性脚本重新用 Markdown 解析器转换（届时需把 `marked` 加回来）。
- **不要因为“历史日志显示成原文”就重新加回**：这是已确认的取舍；正确做法是先迁移数据，而不是恢复隐式判别。

### 9.5 `NoteMention`（@ 提及）

- **原位置**：`frontend/src/utils/noteMention.ts`、`frontend/src/components/note/editor/NoteMentionMenu.vue`（均已删除）；`noteEditorExtensions.ts` 的注册、工具栏“提及成员”按钮、`NoteIcon` 的 `mention` 图标、`note-editor.css` 的菜单样式；依赖 `@tiptap/extension-mention`。
- **当初为什么加**：日志里用 `@` 选中同事，配合 `listUsers` 搜索插入 mention 节点，便于点名与后续扩展通知。
- **为什么删**：产品确认不需要站内 @ 人；候选搜索、弹层交互、权限与通知链路都没有后续需求，维护成本高于收益。
- **删除后的实际行为**：
  - 输入 `@` 只是普通字符，不再弹成员选择框，也不再生成 `data-type="mention"` 节点；
  - 历史正文里的 `<span data-type="mention" data-id data-label>@张三</span>` 打开后按纯文本显示（`@张三`），高亮样式消失；**编辑一次保存后 span 及其属性被丢弃**；
  - 后端 `RichTextGuard` 仍允许这类历史 HTML 通过，不会因提及数据导致保存失败；
  - 工具栏入口、`@tiptap/extension-mention` 依赖与菜单样式一并移除。
- **已知缺口**：删除前未统计提及数据量；文字内容仍在，但如果将来需要恢复“可点击/可通知”的语义，只能重新加回节点或做一次性数据迁移。

### 9.6 `NoteLinkTarget`（站内日志链接识别）

- **原位置**：`frontend/src/utils/noteLinkTarget.ts`（已删除）及测试；消费方 `frontend/src/components/weekly/WeeklyReportEditor.vue` 的 `handleClick` 与 `useRouter`。
- **当初为什么加**：周报正文里的日志引用是站内链接 `/notes/{id}`，希望 Ctrl/Cmd + 单击时用 Vue Router 在应用内跳转，而不是整页打开。
- **为什么能删**：周报正文可编辑（仅生成中为只读），但站内跳转不是必要能力；保留它意味着周报要额外维护一套与 `NoteLinkClick` 不同的点击链路。
- **删除后的实际行为**：周报正文里的 `/notes/{id}` 作为普通链接存在；`buildNoteExtensions` 对 `weekly` 不注册 `NoteLinkClick`，且编辑态 `openOnClick` 为 `false`，所以普通点击只定位光标，Ctrl/Cmd + 单击也不会打开链接；导出/复制时 `href` 仍保留。
- **已知缺口**：无数据影响（纯行为层，不写库）；如果以后希望周报里能打开链接，需要给 weekly 单独注册点击插件，而不是恢复这个文件。

### 9.7 移除补丁时的检查清单

以后再删这类补丁，按这几步走，避免静默数据损失：

1. **先评估数据影响**：有没有历史数据依赖这个节点/属性？用 SQL 或接口统计命中量，必要时先做一次性转换；
2. **确认入口已关闭**：工具栏、斜杠菜单、输入规则、快捷键、粘贴路径都要检查，避免“删了 schema 但还能创建”；
3. **删代码同时删测试**：过期的兼容性测试比没有测试更危险；
4. **在本章留一条记录**：写清“为什么加、为什么删、删后行为、已知缺口”；
5. **更新索引与交叉引用**：§1 装配清单、§8 文件索引、§7 升级检查项。
