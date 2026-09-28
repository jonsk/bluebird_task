# 行为契约：childTaskList.vue → BbSubtaskList

> 源：`bluebird_task_Front/src/views/todolistModule/components/childTaskList.vue`（已读源码 L1-43）

| 项 | 契约内容 |
|---|---|
| **用途** | 任务详情中的「子任务」集合列表（内联在父任务卡/详情下） |
| **输入/状态来源** | `props.taskList`（子任务数组，`TaskCard` 逐项渲染所需的对象列表） |
| **展示规则** | 对 `taskList` 用 `v-for="(item, index)"` 逐项渲染一个任务卡（`TaskCard :task="item"`，`:key="item.id"`）（L12-16）；整体容器：`margin-top:10px; margin-bottom:-10px; padding-left:25px; overflow-x:hidden`（L34-35）；**子任务卡背景覆盖**为 `#f5f7fa`（`childTaskList.vue` L38-40 `:deep(.tast-item){background:#f5f7fa}`） |
| **交互清单** | 本身无独立交互；全部交互委托给逐项渲染的 `BbTaskCard`（勾选完成/折叠/删除/打开抽屉/移动等，见 `BbTaskCard.md`） |
| **业务规则** | 数据源只读展示子任务集合；主任务完成**不强制联动子任务**（02 §4.3）；子任务 `parent_id != null`（02 §4.3） |
| **边界与已知缺陷** | 旧实现用 `:key="index"`（L13）→ 列表变化时**复用 DOM key 出错风险**（新增/删除子任务后状态错位），重写改用 `:key="item.id"` 修正（R16 相关）；旧代码 import 了被注释的 `VueDraggable`（拖拽排序未启用，暂不承诺） |
| **验收用例** | 1) 子任务逐项显示为任务卡；2) 子任务卡背景 `#f5f7fa`；3) 折叠父卡时子列表同步隐藏；4) 删除子任务后 DOM key 稳定不串位；5) 子任务卡交互（完成/删除）与主卡一致 |

> 新组件 `BbSubtaskList` 待导出，契约以旧 `childTaskList.vue` 为准。截图证据待旧前端隔离运行补充。
