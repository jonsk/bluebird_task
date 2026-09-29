# 行为契约：TagConfig/index.vue → BbTagConfig

> 源：`bluebird_task_Front/src/layoutNew/components/TagConfig/index.vue`（已读源码 L1-281；行号为导出时快照，以方法名/类名/字段名为准）
> 依赖源码：`src/store/modules/taskTag.js`（`useTaskTagStore`）、`src/api/todoList/sysTag.js`

- 用途：位于 layout 顶栏的「标签」入口，下拉面板内可查看、重命名、删除、新增用户标签。
- 输入/状态来源：
  - `defineProps`：无。
  - `inject`：无。
  - `store`：`taskTagUseStore()`（`taskTagStore`，状态 `tagList`）；`taskUseStore()`（`taskStore`，源码中**未实际使用**，死引用）。
  - 全局：`getCurrentInstance().proxy`（取 `$modal.msgSuccess/msgError`）。
  - 内部状态：`tagList = reactive([])`（本地可编辑副本）、`tagValue`（新增输入，默认 ""）、`tagRunFlag`（并发/守卫标志，默认 false）。
  - `computed taskTagStoreTagList` 取 `taskTagStore.tagList`；`watch` 其 `deep+immediate`，将 store 数据深拷贝后 `splice/push` 同步到本地 `tagList`。
- 展示规则：
  - 顶栏触发 `el-dropdown`（`trigger="click"`，`@visible-change="changeHandler"`，`:hide-on-click="false"`），默认插槽为 `PriceTag` 图标 + 文本「标签」。
  - 下拉 `el-dropdown-menu`（`class="w-[270px]"`）：
    - 每条标签项：`el-input`（`v-model.trim="tagItem.label"`，`@keydown.enter="updateTag(tagItem)"`，`placeholder="输入要修改的标签名称"`）+ `el-popconfirm`（标题「您确定要删除这个标签吗？」，`@confirm="delTag(tagItem)"`，`placement="top"`），其 reference 内为 `Delete` 图标。
    - `删除` 图标 `v-if="!['重要','紧急'].includes(tagItem.label)"`，即「重要 / 紧急」**不渲染**删除入口。
    - 新增项：`el-input`（`v-model.trim="tagValue"`，`@keydown.enter="runAddTag()"`，`placeholder="输入要添加的标签名称"`）+ `Plus` 图标。
  - 样式：`.delete-btn` 默认 `display:none`，`.el-dropdown-menu__item:hover .delete-btn` 才 `display:block`（删除按钮悬停显示）。
  - 被注释项：`按标签分组` 菜单项、`修改` 图标按钮、`updateBlur` 逻辑等。
- 交互清单：
  - `onMounted` → `taskTagStore.getTags()` → `GET /sysTag/page` → store 将 `res.map(({id,tagName}) => ({label:tagName,value:id}))` 写入 `tagList`。
  - 展开/收起下拉 → `changeHandler(flag)`：`flag=false`（收起）时用 store 最新 `tagList` 深拷贝覆盖本地 `tagList`（丢弃未提交的本地编辑）。
  - 新增（输入框回车）→ `runAddTag`：`tagValue===""` 直接返回；在 `taskTagStore.tagList` 中按 `label` 查重，重复则清空输入 + `msgError("添加的标签已存在")`；`tagRunFlag` 为 true 则返回；置 true → `taskTagStore.addTag(tagValue)`（`POST /sysTag/add`，body `{tagName}`）→ 成功清空输入 + `msgSuccess("标签添加成功")`；失败 `msgError("添加标签失败")`；`finally` 复位 `tagRunFlag`。
  - 重命名（标签输入框回车）→ `updateTag(tagItem)`：`label===""` → `msgError("标签值不能为空")`；按 `value` 在 store 中找 `activeItem`；`activeItem.label === tagItem.label` 则直接返回；按 `label` 查重，若已存在则把 `tagItem.label` 退回 `activeItem.label` + `msgError("标签已存在")`；`tagRunFlag` 守卫；`taskTagStore.updateTag(tagItem)`（`POST /sysTag/update`，body `{id:value, tagName:label}`）成功后 store 内 `getTags()` 刷新，`msgSuccess("标签修改成功")`；失败 `msgError(\`修改标签【${tagItem.label}】失败\`)`。
  - 删除（popconfirm 确认）→ `delTag(tagItem)`：`tagRunFlag` 守卫 → `taskTagStore.delTag(tagItem)`（`POST /sysTag/del?ids=<value>`）成功后 store `getTags()` 刷新 + `msgSuccess("标签删除成功")`；失败 `msgError(\`删除标签【${tagItem.label}】失败\`)`。
  - `runHandler(text)`：仅 `console.log`，switch 内无实际逻辑（死代码/预留）。
- 业务规则：
  - `label` 全局唯一：新增与重命名均以 store 的 `tagList` 做 `label` 查重并阻断。
  - 「重要」「紧急」为内置标签，**不可删除**（不渲染 Delete 图标）。
  - 本地 `tagList` 是 store 的深拷贝副本，输入框直接改本地副本，只有回车/确认才提交；下拉收起时从 store 重新拉平。
  - `tagRunFlag` 为全局单一锁，任一增/改/删进行中会拦截其它操作。
  - 所有网络异常统一 `catch` 弹错，无 optimistic 回滚（标签值以 store 刷新为准）。
- 边界与已知缺陷：
  - `updateTag` 中 `activeItem = taskTagStore.tagList.find(({value}) => value === tagItem.value)` 可能为 `undefined`，随后访问 `activeItem.label` 会抛错（例如自定义新增项未同步 value 或并发刷新后失配）→ 重写须判空并给出兜底。
  - 仅回车提交修改，**失焦不提交且不自动复原**（原 `updateBlur` 已注释）；未回车的改动在收起下拉时才被覆盖。
  - `el-dropdown-item` 本身可点击，但输入框交互与 `hide-on-click=false` 依赖，点击输入框区域行为待确认。
  -「重要/紧急」的删除图标不渲染，但 `el-popconfirm` 仍包裹 reference，是否存在可触发的空确认区域 → 待确认。
  - `taskStore` 引入但未使用；`runHandler` 无效。
  - 新增成功后 store 仅在 `res.id` 存在时 push，若接口不返回 `id` 则新标签不会立即出现（待确认后端返回结构）。
- 验收用例：
  1) 打开页面/下拉后，标签列表来自 `GET /sysTag/page`，按 `id↔value`、`tagName↔label` 映射展示。
  2) 在新增输入框输入已存在的标签名并回车，出现「添加的标签已存在」且输入框被清空，列表不新增。
  3) 修改某标签名后回车，`POST /sysTag/update` 发出且成功后列表刷新；改为空名回车提示「标签值不能为空」；改为已有名则回退原值并提示「标签已存在」。
  4) 悬停标签项显示删除图标；「重要」「紧急」项不显示删除图标。
  5) 删除非内置标签，确认后调用 `POST /sysTag/del?ids=<id>`，成功提示并刷新列表。
  6) 修改输入框但未回车，收起再展开下拉，标签名恢复为 store 中的原值。
