# 设计契约：BbTree（原子组件）

> 类型：Element Plus 薄封装 ｜ 依据：03 §3.4.1 / §6 ｜ 本契约无旧源，属「风格契约」

- 职责：以树形结构展示层级数据（分类树、组织树、自定义栏），统一节点样式、筛选与展开交互。
- 封装基线：基于 `el-tree`
- Props 透传约定：
  - 直接透传：`data`、`node-key`、`props`（`label/children/disabled`）、`default-expanded-keys`、`default-checked-keys`、`show-checkbox`、`check-strictly`、`check-on-click-node`、`filter-node-method`、`highlight-current`、`expand-on-click-node`、`accordion`、`draggable`、`allow-drop`、`indent`、`lazy`、`load`、`current-node-key`。
  - 本组件新增：`size: 'default' | 'small'`（节点密度）、`variant: 'default' | 'rail'`（左栏导航态，缩小内边距与圆角）。
  - 未显式列出的透传项经 `v-bind="$attrs"` 原样下发至 `el-tree`，禁止在业务层直接散写 `el-tree` 样式。
- Emits / Slots / 方法：
  - Emits（透传）：`node-click`、`node-contextmenu`、`check`、`check-change`、`current-change`、`node-expand`、`node-collapse`、`node-drag-*`。
  - Slots（透传）：`default="{ node, data }"`、`empty`。
  - 方法：经 `defineExpose` 暴露 `filter(value)`、`setCurrentKey(key)`、`getCheckedKeys()`、`getCheckedNodes()`、`setCheckedKeys(keys)`，转发内部 `el-tree` ref。
- Design Token 映射：
  - 节点文字：默认 `--bb-color-text-primary`；次级说明 `--bb-color-text-secondary`；禁用 `--bb-color-text-disabled`。
  - 节点悬停背景：`--bb-color-bg-elevated`；选中态文字 `--bb-color-primary`、选中背景为 `--bb-color-primary` 低透明度（8%）。
  - 分隔/缩进线：`--bb-color-border`；圆角：`--bb-radius-sm`（`rail` 态统一）；节点内边距：`--bb-space-1` / `--bb-space-2`。
  - 节点字号：`--bb-font-size-sm` / `--bb-font-size-md`；浮层（右键菜单）阴影：`--bb-shadow-md`。
- 状态矩阵：`default` 常规节点 ｜ `hover` 背景 `--bb-color-bg-elevated` ｜ `active`（current）文字 `--bb-color-primary` + 浅底 ｜ `disabled` 文字 `--bb-color-text-disabled` 且不可点 ｜ `loading` 懒加载节点显示 primary 旋转图标。
- 无障碍基准：
  - `role="tree"`，节点 `role="treeitem"`，并以 `aria-expanded`、`aria-selected`、`aria-disabled`、`aria-level` 标注状态。
  - 键盘可达：`↑/↓` 上下移动、`→/←` 展开/折叠、`Enter` 选中、`Space` 勾选（checkbox 模式）；Tab 进入/退出树。
  - focus ring：`2px` `--bb-color-primary` 外描边，`:focus-visible` 生效。
  - 文字与背景对比度 ≥ 4.5:1，选中/禁用态同样满足。
- 与旧实现的差异：旧分类/组织树通过 `inject("indexPageObj")`、`proxy` 隐式传参且硬编码背景色；新组件改为显式 props，色值全部走 Design Token，空态交由业务层统一使用 `BbEmpty`（经 `empty` 插槽），去掉父级隐式耦合。
- 验收断言：
  - Vitest 快照断言节点 DOM 结构、展开态类、`current`/`checked` 类名与 `aria-*` 属性。
  - 视觉基线（§2.3.2 G7）：对抗 `component-sidebar-leftbox-1440x900.png`，结构化断言节点文字色、选中背景、缩进层级、hover 态；像素 diff 仅作告警。
