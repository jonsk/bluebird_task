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
  - **组件未被挂载 · as-is 不可用（0304/D5）**：`LeftBox/index.vue` 未 `import` 本组件，仅在 L303 声明了一个从未使用的 `categoryTreeRef`；左栏实际只渲染 `OrganizationalMechanismTree`。本组件为孤立/死代码，**旧实现下不可达**；数据源（`GET /categories`）与交互按目标态重写。**去留已确认（0308 产品决策）：保留、必做** —— 分类是系统核心功能（`05` R5 分类与检索），不因旧实现未挂载而废弃；本组件即分类树的**指定承接组件**（技术底座为原子组件 `BbTree`）。同栏的 `BbOrgTree` 为另一功能（组织机制树），已于 0307 **废弃**，二者处置相互独立。
  - 新增、修改接口调用整段被注释，数据仅存内存，刷新即丢失。
  - 空值失焦删除依赖 `node.parent.data.findIndex` 分支：根节点无 `node.parent` 时走 `else` 仅打印，**根节点无法被空值删除**，且 `findIndex` 结果为 `-1` 时 `splice(-1,1)` 会误删末位节点。
  - `treeInputEditFlag` 是单一全局标志，多节点并发编辑/新增会被互相拦截。
  - 编辑态共用同一个 `treeInputRef`，`nextTick` 聚焦可能落到非当前节点。
  - 删除无二次确认。
  - 新增节点使用字符串临时 id `add${id++}`，与后端数值型 id 混用，`node-key="id"` 存在冲突风险。
  - `allowDrag`/`allowDrop` 恒 `true`，无任何拖拽约束。
## 共享范围 UI 细则（ADR-015，修订 0309）

> 分类支持 `scope ∈ {PERSONAL, DEPARTMENT, ORG}`（个人/部门/组织）。数据源 `GET /categories`（返回三者合并树，可选 `?scope=` 过滤）；写接口 `POST/PUT/DELETE /categories`。权限见 `02 §4.7`。

### 节点呈现
- 节点右侧/前置**范围徽标**：`PERSONAL` 无徽标（默认）；`DEPARTMENT` 显示部门名小标签（`el-tag`，`tooltip=部门`）；`ORG` 显示「组织」标签（内置图标）。
- 非本人可写的共享节点（部门非负责人 / 组织非 ADMIN）加**只读锁定图标**，悬停显示「只读」。

### 范围过滤（顶部）
- 树上方分段控件（`el-radio-group`）：**全部 / 个人 / 部门 / 组织** → 映射 `GET /categories?scope=`（「全部」不带参数）。
- 默认「全部」。

### 新增（悬停 `Plus`）
- 点击后选择**范围**（`el-dropdown` 或对话框单选框）：
  - `个人`（默认，所有用户可选）；
  - `部门`（需选 `deptId`：默认取当前用户主部门；部门选择器数据源 `GET /departments`）——**仅** 部门负责人/ADMIN 可见该选项；
  - `组织`——**仅** `ADMIN`/`USER_MANAGER` 可见。
- 提交 `POST /categories {name,parentId,scope,deptId}`；成功后刷新树并按 `id` 定位新节点。

### 编辑（悬停 `Edit`）
- 仅改名（`PUT /categories/{id} {name,...}`）；**范围不可在编辑中随意变更**：`PERSONAL→部门/组织`、跨部门迁移等仅创建者/ADMIN 操作，且 `DEPARTMENT` 必须带 `deptId`。
- 非创建者且非部门负责人：无编辑/删除入口（只读）。

### 删除（悬停 `Close`）
- 二次确认（`BbConfirm`）；共享分类（部门/组织）确认文案提示**影响范围**（如「该部门共享分类，删除将影响部门内成员」）。
- `DELETE /categories/{id}`。

### 拖拽
- 仅允许在**本人可写**范围内拖拽调整 `parentId`；**禁止跨 `scope` 拖拽**（跨范围父级非法），越界时拒绝并提示。
- 落库 `PUT /categories/{id}` 更新 `parentId`；失败回滚树。

### 只读 / 空态 / 加载
- 无写权限的共享节点：仅可展开/选中，无编辑/删除/拖拽。
- 空态 `BbEmpty`；加载中 `v-loading`。

### 与任务的联动
- 点击节点 → 写选中态并触发按分类过滤任务列表（保持原契约 5 条）。

- 验收用例（新增）：
  7. `GET /categories` 含 `scope` 节点时，分别渲染个人/部门/组织徽标；`?scope=DEPARTMENT` 仅显示部门分类。
  8. 普通用户新增仅见「个人」选项；部门负责人可见「部门」并可选定 `deptId`；ADMIN/USER_MANAGER 可见「组织」。
  9. 对非本人可写的部门/组织分类，无编辑/删除/拖拽入口（只读锁定）。
  10. 拖拽跨 `scope` 被拒并提示；同范围内拖拽成功后 `PUT /categories/{id}` 更新 `parentId` 且刷新保持。
  11. 删除部门/组织分类前二次确认且提示影响范围。

- 验收用例：
  1. 加载分类树：截获 `GET /categories` 返回 `fixtures/api/categories/GET.tree.json`，树渲染且默认全部展开。
  2. 悬停节点显示「新增/修改/删除」图标；点击新增后子级出现输入框并自动聚焦。
  3. 输入分类名后回车/失焦 → 调用 `POST /categories` 并刷新树；名称为空失焦 → 该临时节点消失且不发请求。
  4. 修改节点名 → `PUT /categories/{id}`；删除节点 → 先二次确认再 `DELETE /categories/{id}`（新契约补齐确认）。
  5. 点击节点 → 写入 `taskStore.select_task_tree_node` 并触发按分类过滤的任务列表查询（补齐 E-10 选中联动）。
  6. 拖拽节点改变父级后重载数据仍保持（接口待确认：预计以 `PUT /categories/{id}` 更新 `parentId`）。

## 实现状态（2026-09-29）

**已实现**：`frontend/src/components/bb/BbCategoryTree.vue`（原子底座 `BbTree` 未单独抽取，直接使用 `el-tree`），挂载于左栏 `BbFilterRail.vue`；E2E 见 `frontend/e2e/filter-rail.spec.ts`（E-10/E-10b）。

落地要点与契约的对应：

- **选中联动**：点击节点 → `emit('select', id)` → `taskStore.setCategoryFilter(id)` → `GET /tasks?scope=..&categoryId=..`（**子树**过滤，新增查询参数；见 openapi `/tasks` GET）。
- **`PUT` 语义**：后端 `CategoryCmd` 的 `parentId` 为**整体覆盖**（缺省即置空 = 移到根），因此改名与拖拽都必须回传 `parentId`；实现已按此处理。
- **写权限（以 ADR-015 §3 为准，已同步修正后端 `CategoryService`）**：`PERSONAL` 仅创建者；`DEPARTMENT` 创建者/该部门负责人(`leader_user_id`)/`ADMIN`；`ORG` `ADMIN`/`USER_MANAGER`。前端据此显隐写入口与只读锁（`:hasRole` + `ownerId`/`leaderId` 判定）。**注**：本文档早前「组织仅 ADMIN」与「部门非负责人只读」的措辞已按 ADR-015 收敛为上述三条。
- **拖拽**：`allow-drop` 拒绝跨 `scope`（含 `DEPARTMENT` 跨部门）；`node-drop` 落库 `PUT /categories/{id}` 更新 `parentId`，失败回滚（重载树）。
- **范围过滤**：`全部/个人/部门/组织` → `GET /categories?scope=`；按范围过滤时**保留命中节点的祖先层级**（后端 `CategoryService.tree` 与 MSW 一致）。
- **未实现**：节点排序拖拽仅改 `parentId`，`sort` 不落库（`sort` 恒 0，与后端一致）。
