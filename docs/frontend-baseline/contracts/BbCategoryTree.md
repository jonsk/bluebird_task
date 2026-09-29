# 行为契约：categoryTree.vue → BbCategoryTree

> 源：`bluebird_task_Front/src/layoutNew/components/LeftBox/categoryTree.vue`（已读源码；行号为导出时快照，以方法名/类名/字段名为准）

- 用途：左侧栏的「任务分类树」模块，用于展示用户自定义分类层级，并支持分类的新增、改名、删除与拖拽排序。
- 输入/状态来源：props / inject / store / 全局
  - 无 props、无 inject。
  - 数据源 `taskTreeStore.treeList`（Pinia `store/modules/taskTree.js`，state 字段 `treeList`；初始 `[]`）。加载入口 `taskTreeStore.getList()` → 旧 `GET /sys/category/tree`（`api/todoList/tree.js#getTreeList`）。
  - 读取全局 store `taskStore`（`store/modules/task.js`）：字段 `select_task_type`、`select_task_tree_node`。本组件对二者均**只读未写**。
  - 组件本地状态：`treeEditObj`（未实际使用）、`treeInputEditFlag`（`0` 未操作 / `1` 新增 / `2` 修改）、`treeInputRef`（`shallowRef`，共用输入框 ref）、模块级自增 `id`（初值 `1000`，生成临时 `add${id++}`）。
- 展示规则：
  - `el-tree` 绑定 `:data="taskTreeStore.treeList"`，`node-key="id"`、`default-expand-all`、`:expand-on-click-node="false"`、`draggable`、`:highlight-current="true"`；`:allow-drop`/`:allow-drag` 恒返回 `true`。
  - 节点文案使用 `node.label`，外层 `el-tooltip` 展示全文，`div.truncate` 单行省略。
  - 悬停节点时（`.custom-tree-node:hover`）显示 `custom-tree-node-edit` 操作区，含「新增/修改/删除」三个 `el-icon`（`Plus` / `Edit` / `Close`）。
  - 进入编辑态（`data.editInput === true`）时用 `el-input` 原地替换文案，节点文本宽度由 `w-[calc(100%-100px)]` 切到 `w-[100%]`。
  - 容器 `.tree-box` 高度 `h-[calc(100%-430px)]`，`overflow-y: auto`，顶部有分隔线。
- 交互清单：<动作> → <效果> → <接口> → <异常/回滚>
  - 新增（点击 `Plus`）→ `append(data)`：若 `treeInputEditFlag ∈ {1,2}` 直接 return；置 `treeInputEditFlag=1`；生成 `{id:'add'+id++, label:'', editInput:true, children:[]}`，有 `data` 时 push 进 `data.children` 并置 `newChild.parentId=data.id`，否则 push 到根 `treeList`；以数组浅拷贝触发响应式；`nextTick` 聚焦输入框 → **无接口调用**（`addHander`/新增接口被注释）→ 无回滚。
  - 修改（点击 `Edit`）→ `update({node,data})`：`treeInputEditFlag ∈ {1,2}` 时 return；置 `2`；`data.editInput=true`；`nextTick` 聚焦 → **无接口调用**（更新接口被注释）→ 无回滚。
  - 删除（点击 `Close`）→ `remove(node,data)`：`node.parent` → `children = parent.data.children || parent.data`，按 `id` `findIndex` 后 `splice`，并浅拷贝 `treeList` → **无二次确认、无接口调用**（store `delHandler`/`delTree` 未在此处调用）→ 无回滚。
  - 输入框 `@keyup.enter` / `@blur` → `treeInputBlur({node,data})`：`data.label === ''` 时调用内部 `delHandler()` 删除该临时节点；有值时仅 `data.editInput=false`、`treeInputEditFlag=0`，**接口调用被注释**。
  - 点击节点文案 → `handlerTreeClick({node,data})`：**仅 `console.log`**，不写 `taskStore.select_task_tree_node`，不触发任务列表查询。
  - 拖拽 → `handleDragStart/handleDragEnter/handleDragLeave/handleDragOver/handleDragEnd/handleDrop`：**全部仅 `console.log`**，拖拽结果不落库、不调接口。
  - `defineExpose({ append })`：对外暴露 `append`。
- 业务规则：
  - **选中联动缺失**：`taskStore.select_task_tree_node` 在 `task.js` 中定义，被 `getTskListByType` 打印（`console.log("当前的分类树节点", ...)`），但**本组件从不写入**，也无按分类过滤任务的查询参数，故分类筛选链路实际未闭合。
  - **与 `taskStore.select_task_tree_node` 的关系**：契约要求重写后点击节点应 `setSelectTaskTreeNode` 并按节点过滤任务列表（旧实现未达成）。
  - **默认选中**：无 `setCurrentKey` / 无默认高亮节点。
  - 新增/改名/删除当前仅内存态，接口为注释占位；旧接口语义（`addTree`/`updateTree`/`delTree`，`api/todoList/tree.js`）对应新接口 `POST /categories`、`PUT /categories/{id}`、`DELETE /categories/{id}`（E2E E-10）；删除应二次确认。
- 边界与已知缺陷：
  - **组件未被挂载 · as-is 不可用（0304/D5）**：`LeftBox/index.vue` 未 `import` 本组件，仅在 L303 声明了一个从未使用的 `categoryTreeRef`；左栏实际只渲染 `OrganizationalMechanismTree`。本组件为孤立/死代码，**旧实现下不可达**；数据源（`GET /categories`）与交互按目标态重写，去留由 M1 产品确认（注：同栏的 `BbOrgTree` 已于 0307 产品决策**废弃**，本组件仍待定）。
  - 新增、修改接口调用整段被注释，数据仅存内存，刷新即丢失。
  - 空值失焦删除依赖 `node.parent.data.findIndex` 分支：根节点无 `node.parent` 时走 `else` 仅打印，**根节点无法被空值删除**，且 `findIndex` 结果为 `-1` 时 `splice(-1,1)` 会误删末位节点。
  - `treeInputEditFlag` 是单一全局标志，多节点并发编辑/新增会被互相拦截。
  - 编辑态共用同一个 `treeInputRef`，`nextTick` 聚焦可能落到非当前节点。
  - 删除无二次确认。
  - 新增节点使用字符串临时 id `add${id++}`，与后端数值型 id 混用，`node-key="id"` 存在冲突风险。
  - `allowDrag`/`allowDrop` 恒 `true`，无任何拖拽约束。
- 验收用例：
  1. 加载分类树：截获 `GET /categories` 返回 `fixtures/api/categories/GET.tree.json`，树渲染且默认全部展开。
  2. 悬停节点显示「新增/修改/删除」图标；点击新增后子级出现输入框并自动聚焦。
  3. 输入分类名后回车/失焦 → 调用 `POST /categories` 并刷新树；名称为空失焦 → 该临时节点消失且不发请求。
  4. 修改节点名 → `PUT /categories/{id}`；删除节点 → 先二次确认再 `DELETE /categories/{id}`（新契约补齐确认）。
  5. 点击节点 → 写入 `taskStore.select_task_tree_node` 并触发按分类过滤的任务列表查询（补齐 E-10 选中联动）。
  6. 拖拽节点改变父级后重载数据仍保持（接口待确认：预计以 `PUT /categories/{id}` 更新 `parentId`）。
