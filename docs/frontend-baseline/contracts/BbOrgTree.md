# 行为契约：OrganizationalMechanismTree.vue → BbOrgTree

> 源：`bluebird_task_Front/src/layoutNew/components/LeftBox/OrganizationalMechanismTree.vue`（已读源码；行号为导出时快照，以方法名/类名/字段名为准）

- 用途：左侧栏的「组织机构」树，按部门/人员层级展示组织机制（旧实现为静态演示数据，作为重写为真实部门树的行为基线）。
- 输入/状态来源：props / inject / store / 全局
  - 无 props、无 inject、无 store 依赖。
  - 树数据 `data: Tree[]` 为**组件内硬编码常量**：`科信部 > XXX部门 > 张三` 及占位 `Level one 2 / Level one 3` 及其子级。
  - `defaultProps = { children: 'children', label: 'label' }`。
  - 可见性由父组件 `LeftBox/index.vue` 的 `radioType` 控制：`<OrganizationalMechanismTree v-if="radioType === '2'"/>`，而 `radioType` 默认 `'1'`。
- 展示规则：
  - `el-tree` 绑定 `:data="data"`、`:props="defaultProps"`、`accordion`（手风琴：同层仅展开一个节点）、`style="max-width: 600px"`。
  - 节点文案取 `label`；节点点击有 Element Plus 默认高亮；无自定义节点插槽、无图标、无 tooltip。
- 交互清单：<动作> → <效果> → <接口> → <异常/回滚>
  - 点击节点 → `handleNodeClick(data)`：**仅 `console.log(data)`** → 无写 store、无查询、无接口、无异常处理。
  - 展开/收起 → `accordion` 由 el-tree 内部管理，无业务副作用。
  - 无新增/修改/删除/拖拽/搜索等交互。
- 业务规则：
  - 与 `taskStore` 无任何关系：不写 `select_task_tree_node`，不触发任务列表查询，无选中联动。
  - 无默认选中节点，无部门筛选/选人联动。
  - 重写契约应对接新接口 `GET /departments`（部门树，`docs/api/openapi.yaml` `/departments`；样例 `fixtures/api/departments/GET.tree.json`），并与选人按部门筛选场景（E2E E-12，`/users?deptId=&keyword=`）联动。
- 边界与已知缺陷：
  - 纯静态 mock，组织机构数据从未接入后端接口，无法反映真实部门/人员。
  - `accordion` 行为与分类树的 `default-expand-all` 不一致，用户体验不统一。
  - **实际不可达**：切换 `radioType` 的 `el-radio-group` 在 `LeftBox/index.vue` 中被注释，`radioType` 恒为 `'1'`，故本组件默认不渲染。**去留决策（0303/N4）**：属"待确认"，需产品在 M1 明确——保留则应提供可用视图切换入口（本契约验收用例 1），废弃则从组件清单移除；在此之前**不阻断基线冻结**。
  - 无搜索/过滤、无虚拟滚动，大组织树会有性能与可用性问题。
  - 无人员脱敏处理，同层节点既可能是部门也可能是人员但无区分标识。
- 验收用例：
  1. `radioType === '2'` 时渲染组织树；`radioType === '1'` 时不渲染（现状）；新组件需提供可用的视图切换入口。
  2. 数据改为 `GET /departments`（fixtures `fixtures/api/departments/GET.tree.json`），按 `id/name/parentId/children` 渲染部门层级。
  3. 点击部门节点 → 触发按部门筛选（写入选中态并查询 `/users?deptId=` 或过滤任务列表）。
  4. `accordion` 展开收起行为符合预期，同层仅一个节点展开。
  5. 人员节点可见且展示脱敏手机号；人员节点不参与部门级筛选（待确认）。
  6. 无数据时展示空态而非报错。
