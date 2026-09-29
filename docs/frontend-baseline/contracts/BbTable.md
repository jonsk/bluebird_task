# 设计契约：BbTable（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- **职责**：统一数据表格展示（用户/部门管理、审计日志），提供排序、多选、加载与空态的一致性外观。
- **封装基线**：基于 `el-table`
- **Props 透传约定**：
  - 直接透传：`data`、`row-key`、`border`、`stripe`、`size`、`height`、`max-height`、`show-header`、`show-overflow-tooltip`、`highlight-current-row`、`default-sort`、`span-method`、`row-class-name`、`header-cell-class-name`、`empty-text`、`lazy`、`tree-props`。
  - 本组件新增：`variant: 'default' | 'audit'`（审计日志斑马纹/紧凑）、`loadingTarget`（默认内部局部 `v-loading`）。
  - 列定义：`el-table-column` 由业务侧经默认插槽书写，本组件只负责容器主题；禁止业务层另写表格边框/表头背景。
- **Emits / Slots / 方法**：
  - Emits（透传）：`selection-change`、`select`、`select-all`、`sort-change`、`row-click`、`row-dblclick`、`current-change`、`expand-change`、`header-click`。
  - Slots（透传）：`default`（列）、`empty`（空态，业务层放 `BbEmpty`）、`append`。
  - 方法：`defineExpose` 暴露 `clearSelection()`、`toggleRowSelection(row, selected)`、`toggleAllSelection()`、`sort(prop, order)`，转发内部 `el-table` ref。
- **Design Token 映射**：
  - 表头背景：`--bb-color-bg-elevated`；表格边框/分割线：`--bb-color-border`；容器圆角：`--bb-radius-md`；容器阴影：`--bb-shadow-sm`。
  - 行悬停背景：`--bb-color-bg-elevated`；选中行背景：`--bb-color-primary` 低透明度（8%）。
  - 单元格文字：`--bb-color-text-primary`；次级列/说明：`--bb-color-text-secondary`；空态文案：`--bb-color-text-secondary`。
  - 单元内边距：`--bb-space-2` / `--bb-space-3`；字号：`--bb-font-size-md`（表体）/ `--bb-font-size-sm`（紧凑）。
- **状态矩阵**：`default` 常规行 ｜ `hover` 行背景 `--bb-color-bg-elevated` ｜ `active` 当前/选中行 primary 浅底 ｜ `disabled`（禁用行）文字 `--bb-color-text-disabled` ｜ `loading` 经 `BbLoading` 局部遮罩，`aria-busy="true"`。
- **无障碍基准**：
  - `role="table"`（或 `grid`），表头 `scope="col"`，排序列以 `aria-sort`（`ascending`/`descending`/`none`）标注。
  - 键盘可达：表头排序控件与行内操作可 Tab 聚焦；`focus ring` 使用 `2px` `--bb-color-primary`。
  - 文字与背景对比度 ≥ 4.5:1，斑马纹不得破坏对比度。
- **与旧实现的差异**：旧若依 `system/monitor` 表格样式随脚手架删除；审计日志按新规范统一复用 `BbTable + BbPagination`（03 §5.6）。新组件不再依赖若依主题变量，全部经 Design Token。
- **验收断言**：
  - Vitest 快照断言列头文案、行 DOM、`aria-sort` 与空态文案（经 `empty` 插槽）。
  - 视觉基线（§2.3.2 G7）：结构化断言表头背景、边框、斑马纹、hover/选中行色；像素 diff 作告警。
