# 行为契约：selectUser.vue → BbUserSelect

> 源：`bluebird_task_Front/src/views/todolistModule/components/selectUser.vue`（已读源码 L1-266；行号为导出时快照，以方法名/类名/字段名为准）
> 依赖源码：`src/api/system/user.js`（`listUser`/`getIdsByUser`）、`src/store/modules/task.js`；调用方 `layoutNew/components/RightBoxDialog/index.vue`、`views/todolistModule/components/addTaskBlock.vue`

- 用途：任务系统里的「选择接收用户」弹窗，支持按本部门/全部用户切换、名称模糊搜索、分页、多选并回显，确认后向父组件回传已选用户对象数组。
- 输入/状态来源：
  - `defineProps`：为空（`roleId` 已注释），无外部 props。
  - `inject`：无。
  - `store`：`import taskUseStore from "@/store/modules/task.js"`，声明 `taskStore`（L138-139），但源码中**未实际使用**（死引用）。
  - 全局：`getCurrentInstance().proxy` 用于取 `$refs`（`refTable`）与 `resetForm`；`unref` 为全局自动导入。
  - 内部状态：`visible`（弹窗显隐，默认 false）、`switchValue`（默认 true=本部门）、`loading`、`userList`、`total`、`userItems`（已选用户集合）、`queryParams = reactive({page:1,size:10,username:'',istbm:0})`。
- 展示规则：
  - `el-dialog` 标题「选择接收用户」，宽 `1024px`，`top="5vh"`，`destroy-on-close`，`append-to-body`，`@closed="handleClose"`。
  - 表单项：`用户名称` 输入框（绑定 `queryParams.username`，`placeholder="支持模糊搜索"`，`clearable`，回车触发 `handleQuery`）；另有两个被注释的表单项（`name`、`department`）。
  - 右侧 `el-switch`：绑定 `switchValue`，`active-text="本部门用户"` / `inactive-text="全部用户"`，`@change="handleSwitch"`。
  - `el-table`（`ref="refTable"`，`height="500px"`，`v-loading="loading"`，`@row-click="clickRow"`，`@selection-change="handleSelectionChange"`）：多选列 + `用户名称`（`prop="name"`，悬浮 `el-popover` 展示 `username`）+ `所属部门`（`prop="department"`）；`账号/昵称/邮箱/手机/状态/创建时间` 列均被注释。
  - `pagination`：`v-show="total > 0"`，双向绑定 `queryParams.page`/`queryParams.size`，`@pagination="getList"`。
  - 底部：`确定`（`handleSelectUser`）、`取消`（`handleClose`）。
- 交互清单：
  - 外部调用 `show(ids)` → 清空 `userItems` → `getList()` → 若传 `ids` 再 `getIdsByUserFun(ids)` → `visible=true`。（`defineExpose({show})`）
  - 点击 `搜索` / 输入框回车 → `handleQuery` → `page=1` → `getList` → `GET /admin/user/list`（params：`page,size,username,istbm`）→ 读 `res.list`/`res.total`；失败由 request 层统一处理，无本地回滚。
  - 点击 `重置` → `resetQuery` → `proxy.resetForm("queryRef")` + `username=''`、`istbm=0` → `handleQuery`；**未重置 `switchValue`**。
  - 切换「本部门/全部用户」→ `handleSwitch` → `getList`（`istbm = switchValue ? 1 : 0`）。
  - 点击表格行 → `clickRow` → `proxy.$refs["refTable"].toggleRowSelection(row)`。
  - 勾选/取消 → `handleSelectionChange(selection)` → 以 `id` 去重后并入 `userItems`。
  - `getList` 内 `nextTick` 用 `toggleRowSelection(row,true)` 恢复已选态。
  - 点击 `确定` → `handleSelectUser` → `console.log` → `visible=false` → `emit("confrim", unref(userItems))`（注意拼写为 `confrim`）。
  - 点击 `取消` 或弹窗 `@closed` → `handleClose` → 关闭 + 清空 `userItems` + `page=1`、`username=''`、`istbm=0`。
  - `show(ids)` 内 `getIdsByUserFun(ids)` → `getIdsByUser({userids:ids})` → `GET /admin/user/getListByIds` → `userItems = res || []`。
- 业务规则：
  - `emit` 事件名固定为 `confrim`（非 `confirm`），父组件须用 `@confrim` 接收；payload 为已选用户对象数组（父组件再取 `{id,name}` 拼接 `ids`/`names`）。
  - 父组件 `RightBoxDialog` 调用 `selectUserRef.show(taskStore.dialog_right_config_Obj.userIdList || '')`，即传入**逗号拼接字符串**，而 `getIdsByUserFun` 将其作为 `userids` 参数上报（`userids` 期望数组还是逗号串 → 待确认，以接口文档/后端为准）。
  - 选中用户跨分页/跨搜索保留（`userItems` 累积），并在列表刷新后尝试回显。
  - 去重唯一键为 `row.id`。
- 边界与已知缺陷：
  - **取消勾选不会移除**：`handleSelectionChange` 仅做「并集」追加，从不从 `userItems` 删除，取消选中后仍会回传，需在重写时修正。
  - **回显竞态**：`show` 先 `getList()` 后异步 `getIdsByUserFun`，`getList` 内 `nextTick` 回显时 `userItems` 可能仍为空，导致首屏已选行未勾选（重写应等两请求均完成后再回显）。
  - `getIdsByUserFun` 无 `.catch`，仅成功分支赋值，异常被吞（request 层弹错）。
  - `resetQuery` 未同步 `switchValue`，与 `istbm` 显示可能不一致。
  - `taskStore` 引入但未使用。
  - 用户名称列展示 `prop="name"`，而模糊搜索字段为 `username`，二者语义不同（待确认产品预期）。
- 验收用例：
  1) 父组件调用 `show()`（不传 ids）后弹窗出现并加载第一页用户，默认「本部门用户」为开。
  2) 勾选若干用户 → 翻页/搜索后返回，原勾选仍保留，且已选不重复。
  3) 点击「确定」父组件收到 `confrim` 事件与选中数组（含 `id`、`name`），可拼出 `ids`/`names`。
  4) 点击「取消」再重新打开，弹窗内无残留勾选，页码回到 1。
  5) `show("<ids>")` 传入已选 id 后，对应行在列表加载完成后应处于勾选态（当前实现存在竞态，重写后须通过）。
  6) 切换「全部用户」，请求 `istbm=0`；切回「本部门用户」，请求 `istbm=1`。
