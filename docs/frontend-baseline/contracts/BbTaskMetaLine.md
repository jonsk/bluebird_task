# 行为契约：TaskCard.vue「元信息行」→ BbTaskMetaLine

> 源：`bluebird_task_Front/src/views/todolistModule/components/TaskCard.vue`（已读源码 L1-472；元信息行模板 L64-130，脚本 `formatDateStrOne` L243-248、`getTaskDateStatusClass` L254-268、`tagColor` L397-405，样式 L440-471；行号为导出时快照，以方法名/类名/字段名为准）
> 依赖源码：`bluebird_task_Front/src/utils/utils.js`（`fomatDateFun(time,'dateObj')` L18-81）；调用方/父级 `taskListOne.vue`、`taskListTwo.vue`、`childTaskList.vue`（逐条传 `task`）。

- 用途：任务卡标题下方的一行「元信息」展示区，聚合创建时间/执行人、子任务进度、截止日期、重复文案、提醒文案、标签、附件图标、@人员，供列表快速扫读；本区域自身不可编辑，点击行进入详情。
- 输入/状态来源：
  - `props`：`task`（`defineProps(["task"])`，L237）。字段来源：`task.content`、`task.completeStatus`、`task.belongUserId`、`task.belongUserName`、`task.taskSetupTime`、`task.completedtotal`、`task.total`、`task.dateText`、`task.eachText`、`task.tipsText`、`task.taskTypeNames`、`task.fileList`、`task.userNameList`。
  - `inject`：`indexPageObj`（`reactive(inject("indexPageObj", {}))`，L230），仅用于子任务新增后刷新，元信息行本身不消费。
  - `store`：`taskUseStore`（`taskStore.select_task_type`）、`useUserStore`（`userStore.id`）。
  - 全局：`useRouter()`，`isMyDo = computed(() => router.currentRoute.value.name === "MyDo")`（L250-252）。
- 展示规则：
  - 容器 `.task_details`（`flex items-center`，`flex:1; overflow:hidden`，L440-452）。
  - 「创建：」`span.span-generated-on`（宽度 `isMyDo ? '165px' : '120px'`，L65）：MyDo 路由下先渲染 `task.belongUserName`（L66 的 `<text>`），再拼接 `formatDateStrOne(task.taskSetupTime)`（L67），格式化结果为 **「M月D日」**（`formatDateStrOne` 用 `fomatDateFun(new Date(date),"dateObj")` 取 `month`/`day`，L243-248）。非 MyDo 不显示执行人名。
  - 子任务进度 `span.span-step-total`：`v-if="task.total != 0"`（严格不等 0），文案 `{{task.completedtotal}} / {{task.total}}`（L69；代码用 `completedtotal` 小写 t，注意与常规 `completedTotal` 大小写不一致）。
  - 截止日期：`v-if="task.dateText"`，`Calendar` 图标 + `task.dateText` **原文直出**（未格式化），图标与文本均套 `:class="getTaskDateStatusClass(task.dateText)"`（L70-82）。
  - 重复：`v-if="task.eachText"`，`Refresh` 图标 + `task.eachText` 原文（L83-88）。
  - 提醒：`v-if="task.tipsText"`，前置圆点 `.dian-icon` + 铃铛图标（`:class="getTaskDateStatusClass(task.tipsText)"`；类名为空 → `<Bell>`，否则 `<BellFilled>`，L91-94）+ `task.tipsText` 原文（L95-98）。
  - 标签：`v-if="task.taskTypeNames"`，前置圆点 + `PriceTag` 图标 `:color="tagColor(task.taskTypeNames)"` + 文本超长省略 + `el-tooltip` 显示全文（L100-112）。
  - 附件：`v-if="task.fileList.length"`，前置圆点 + `Paperclip` 图标，**仅图标无文字**（L113-119）。
  - @人员：`v-if="task.userNameList"`，前置圆点 + 字面 `@` + `task.userNameList` 超长省略 + `el-tooltip` 全文（L120-129）。
  - 着色规则（旧实现为硬编码 CSS 类，非 token）：
    - 逾期 `diffDays <= 0` → `.task-date-overdue { color: red }`（`getTaskDateStatusClass` L262-263，样式 L469-471）。
    - 临期 `diffDays < 5`（且 > 0）→ `.task-date-near { color: green }`（L264-265，样式 L466-468）。
    - 其余返回 `''`（`getTaskDateStatusClass` L267）；`diffDays = Math.ceil((taskDate - now)/86400000)`，`taskDate = new Date(dateText).getTime()`（L259-261）。
    - 标签 `tagColor`（L397-405）：`includes("紧急")` → `"red"`；否则 `includes("重要")` → `"blue"`；否则 `"default"`（Element Plus 默认色，注意非 `--bb-color-*`）。
  - 空值隐藏：`dateText`/`eachText`/`tipsText`/`taskTypeNames`/`userNameList` 为空字符串或缺失时对应片段整体不渲染；`fileList` 为空数组时不渲染附件图标；`total==0` 不显示进度。
  - 文本截断：`.span-w { min-width:40px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }`，`.lone-w { max-width:150px }`（标签与 @人员复用，L443-451）。
- 交互清单：
  - 点击元信息行所在容器（`.px-2` 的 `@click="taskStore.openTaskRightBox(task)"`，L53-54）→ 写入 `dialog_right_config_Obj` 并显示右侧详情抽屉 → 无独立接口，纯 store 状态；异常无回滚（由 store 内部处理）。
  - 元信息行内文件/标签/@人员 tooltip → 仅悬浮展示，无点击行为。
  - 标题（`task.content`）超出用 `el-tooltip` 展示全文，不属本区域但同行渲染（L55-63）。
- 业务规则：
  - 日期状态仅由「与当前时间的天数差」决定，与任务完成态无关：已完成任务只要 `dateText` 逾期仍显示红色（**待确认**产品是否要求在完成态取消着色）。
  - `isMyDo` 仅影响是否展示 `belongUserName` 及「创建：」段宽度，不改变其它字段。
  - 展示顺序固定：创建 → 进度 → 日期 → 重复 → 提醒 → 标签 → 附件 → @人员（模板书写顺序）。
  - `task.dateText`/`tipsText`/`eachText` 均为**后端已格式化的展示字符串**，前端只做直出与状态着色，不做二次格式化（`formatDateStrOne` 对 `dateText`/`tipsText` 的调用已被注释，L75、L79、L96）。
- 边界与已知缺陷：
  - `v-if="task.fileList.length"` 未做 `fileList` 存在性判断，`fileList` 为 `undefined` 时会抛 `Cannot read properties of undefined (reading 'length')`（L113）。
  - 进度字段名 `completedtotal` 全小写，与常见 `completedTotal` 不一致，易在重写时错配（L69）。
  - `getTaskDateStatusClass` 对**非日期文本**（如 `tipsText` 为「上午 9:00」等）调用 `new Date()` 可能得到 `Invalid Date` → `getTime()` 为 `NaN` → `diffDays` 为 `NaN` → 所有比较为 false → 返回 `''`，铃铛保持空心（L259-267；`BellFilled` 仅在前两类命中时出现）。
  - 颜色为硬编码 `red`/`green` 与 `el-icon` 的 `red`/`blue`，与设计系统语义色 `--bb-color-overdue`/`--bb-color-near` 尚未对齐（03 §设计 Token）：重写须映射到 token。
  - 标签着色仅字符串包含「紧急/重要」，多标签拼接串（如「重要,紧急」）按首次命中「紧急」优先判红，顺序敏感（L397-405）。
  - 日期状态类同时赋给图标与文本，图标颜色随状态变化；`span-w` 最小宽 40px 的历史约束可能导致短文案留白（L443-445）。
  - `taskItemWidth` 由 `onMounted` 的 `document.querySelector('.tast-item')` 计算并传给标题截断宽度（L408-411），不在本区域但影响同卡布局重叠，属脆弱实现（见 BbTaskCard）。
- 验收用例：
  1. `dateText` 为昨天（`diffDays<=0`）→ 日期图标与文本红色；为明天（`0<diffDays<5`）→ 绿色；为 30 天后 → 默认色。
  2. `taskTypeNames` 含「紧急」→ 标签图标红；仅含「重要」→ 蓝；其它 → 默认色。
  3. `total!=0` 显示「{completedtotal} / {total}」；`total==0` 不显示进度。
  4. `dateText`/`eachText`/`tipsText`/`taskTypeNames`/`userNameList` 任一为空字符串时对应图标与文本均不渲染；`fileList` 为空数组时不显示回形针。
  5. MyDo 视图「创建：」前显示 `belongUserName` 且宽 165px；非 MyDo 不显示人名且宽 120px。
  6. 点击元信息行打开右侧详情抽屉。
