# 行为契约：RightBoxDialog 内附件区（el-upload + task-file-list）→ BbAttachmentList

> 源：`bluebird_task_Front/src/layoutNew/components/RightBoxDialog/index.vue` 附件区（模板 L246-296、脚本 L599-689）+ 数据映射 `bluebird_task_Front/src/store/modules/task.js`（`formatServerObj`/`formatTaskObj`）；另 `TaskCard.vue` 仅在 `fileList.length` 时渲染回形针图标。全仓 `glob **/*[Ff]ile*.vue` 未发现独立附件组件，`task-file-list` 仅为 `RightBoxDialog` 内的 div 类名（`ImageUpload`/`FileUpload`、`system/user` 等为系统通用组件，与任务附件无关）。

- 用途：任务详情抽屉中的附件子区域：上传、列表展示、点击预览、删除。无独立组件，逻辑内联于 `RightBoxDialog`。
- 输入/状态来源：
  - `props`：无。
  - `store`：`taskUseStore.dialog_right_config_Obj`（当前任务编辑对象）。
    - `fileList`：由 `formatServerObj` 从后端 `taskFileList` 填充（`task.js` L355-358）。
    - 只读状态：`taskDisabled`（见 `BbTaskDetailDrawer.md`）。
  - `v-model:file-list="taskStore.dialog_right_config_Obj.fileList"` 双向绑定（`el-upload`）。
  - 全局：`uploadUrl = import.meta.env.VITE_APP_BASE_API + '/sys/file/upload'`；`headers = { Authorization: getToken() }`；`import.meta.env.VITE_APP_FILE_URL`（KKFileView 预览服务）。
- 展示规则：
  - 上传入口：`el-upload`（`name="file"`，`:show-file-list="false"`，`:disabled="taskDisabled"`），图标 `Paperclip` + 文案「添加文件」。
  - 列表：`.task-file-list` 内 `v-for="(fileItem, fileIndex) in fileList"`，每行 `:key="fileIndex"`；左侧 `Tickets` 图标 + 文件名 `fileItem.fileName || fileItem.name`；右侧 `Close` 删除图标 `v-show="!taskDisabled"`。
  - 文件名超长 `break-all`、宽度 `calc(100%-40px)`；悬停变绿色。
- 交互清单：
  - 选择文件上传前 → `handleBeforeUpload(file)` → 校验文件名重复 + 大小 → 失败 `$modal.msgError` 并 `return false`（不上传）。
  - 上传成功 → `handleUploadSuccess(res, file)` → `$modal.msgSuccess('上传成功')` → `updateRightItemHandler({type:'fileList'})` → 旧 `POST /task/record/update`（`taskFileIds`），新 `PUT /tasks/{id}`；上传地址旧 `POST /sys/file/upload`，新 **待确认**（对齐 03 §5.3.2 文件接口，fixtures 见 `docs/frontend-baseline/fixtures/api/files/`）。
  - 上传失败 → `handleUploadError()` → `$modal.msgError('插入失败')`。
  - 点击文件行 → `openFile(fileItem)` → `previewFile(fileItem.response || fileItem)` → `window.open` KKFileView 预览（`utils.js` `previewFile`，拼接 `/sys/file/download/{id}` 后 Base64 编码）。
  - 删除文件 → `deleteFile(fileItem)` → 按 `id`/`fileItem.response.id` 定位并在 `fileList` `splice` → `updateRightItemHandler({type:'fileList'})`。
- 业务规则：
  - 上传大小限制 `fileSize = 5`(MB)：`file.size / 1024 / 1024 < 5`。
  - 文件名去重：与当前 `dialog_right_config_Obj.fileList` 中 `fileName` 或 `name` 任一相等即拒绝，`msgError('文件名称不能重复!')`。
  - 文件类型校验已注释，当前**不限类型**（原 `image/jpeg|jpg|png|svg` 校验不生效）。
  - 提交后端时 `formatTaskObj` 将 `fileList` 映射为 `taskFileIds`：有 `fileName` 取 `fileItem.id`，否则取 `fileItem.response.id`，`join(',')`（`task.js` L435-443）。
  - 已完成任务或非归属人（`taskDisabled`）不可上传、不可删除。
- 边界与已知缺陷：
  - 上传失败后 `el-upload` 的 `v-model:file-list` 可能已把失败文件并入 `fileList`，`handleUploadError` 未清理，导致列表出现幽灵条目/重复名校验误判（重写需在 error 回调移除）。
  - 删除附件**无二次确认**（`before-remove`/`on-remove` 钩子被注释），且直接改本地 `fileList` 后调接口，接口失败不回滚。
  - `deleteFile` 以 `id === fileItem.id || id === fileItem.response.id` 定位；`fileItem.response` 为空时存在读取 `undefined.id` 风险。
  - `handleUploadSuccess` 仅以 `if(res)` 判成功，未校验业务码；`updateRightItemHandler` 失败静默。
  - 预览依赖外部 KKFileView（`VITE_APP_FILE_URL`）与下载地址 `/sys/file/download/{id}`，文件 `id` 缺失时预览无效。
  - 列表 `:key="fileIndex"` 使用下标，删除中间项可能复用节点。
  - `fileList` 同时被详情表单与附件区复用，上传项（含 `response`）与历史项（含 `fileName`）结构不一致，靠 `fileName || name` 与 `response` 兜底。
- 验收用例：
  1. 上传 >5MB 文件被拒并提示「上传文件大小不能超过 5 MB!」，不发起请求。
  2. 上传与已有文件同名被拒并提示「文件名称不能重复!」。
  3. 上传成功 → 列表新增该项、提示「上传成功」、调用任务更新保存 `taskFileIds`。
  4. 点击文件名在新窗口打开 KKFileView 预览地址。
  5. 删除文件 → 列表移除并调用更新接口保存；已完成/他人任务看不到删除入口且 `el-upload` 禁用。
  6. 上传失败 → 提示「插入失败」，列表不应残留失败条目（当前为待修正点）。
