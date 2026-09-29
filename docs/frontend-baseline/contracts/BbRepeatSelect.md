# 行为契约：dropdownSetEach.vue → BbRepeatSelect

> 源：`bluebird_task_Front/src/views/todolistModule/components/dropdownSetEach.vue`（已读源码 L1-365；行号为导出时快照，以方法名/类名/字段名为准）

- 用途：任务「重复」选择器。通过 `el-dropdown` 提供每天 / 每周 / 每月 / 每年快捷项、自定义周期（数量 + 天/周/月/年，周可选星期）以及「从不重复」，产出 `eachText`（对应后端 `taskCycle`）。
- 输入/状态来源：
  - props：`dataType: String = "1"`、`dataText: String = ""`（`v-model:dataText`）。
  - emits：`update:dataText`、`confirm`。调用方（`RightBoxDialog/index.vue` L165-169）以 `dataType="0"`、`v-model:dataText="...eachText"`、`@confirm="updateRightItemHandler({type:'eachText'})"` 接入。
  - 无 inject / store 直接依赖；仅工具函数 `fomatDateFun`（实际在模板逻辑中未使用）。
  - 本地状态：`dropdownRef`、`selectValueShow`、`selectDataValue = ref(1)`、`selectDataTypeValue = ref("0")`、`selectDataTypeValueOptions`（reactive）、`weeksOptions`（reactive）、`selectWeeksValue = reactive([])`、`selectDataBtnDisabled`（computed）、`date/weekIndex/listConfig`。
- 展示规则：
  - `el-dropdown`：`hide-on-click=false`、`trigger="click"`、`popper-class="dropdownNotOverflow"`，`@visible-change="changeDropdown"`；默认插槽 `@click="showDropdown"` 手动 `handleOpen`。
  - `selectValueShow=false` 时显示菜单 `listConfig`（每天 / 每周 / 每月 / 每年，图标均 `Timer`；「工作日」与「自定义」项已注释掉）+（条件）「从不重复」（`dataType === '1' && dataText` 时显示，红字，图标 `Delete`）。
  - `selectValueShow=true` 时显示自定义面板：`el-input type="number" min="1"`（数量）+ `el-select`（选项 `天=0 / 周=1 / 月=2 / 年=3`）+ 仅在 `selectDataTypeValue === '1'` 时显示的星期行 `weeksOptions`（一/二/三/四/五/六/日，选中态 class `active`，品牌底色）+「保存」按钮（`selectDataBtnDisabled` 时禁用）。
- 交互清单：
  - 「每天」「每周」「每月」「每年」→ `textValue = text`；若为「每周」则 `textValue = 每周，星期${num_to_chinese(weekIndex)}` → `update:dataText(textValue)` → `confirm` → `closeDropdown`。
  - 「从不重复」→ `update:dataText("")` → `confirm` → 关闭。
  - 「自定义」→ `selectValueShow = true`（分支保留，但菜单项已注释，UI 不可达）。
  - `selectWeeksHandler(week)` → 已选则移除、未选则 push（toggle）。
  - 「保存」（`confirmSelectData`）→ 计算 `valueText` → `update:dataText(valueText)` → `confirm` → 关闭。
  - 下拉 `visible-change=false` 且 `selectValueShow` 为真 → `closeDropdown`；`closeDropdown` 重置 `selectDataValue=1`、`selectDataTypeValue="0"`、清空 `selectWeeksValue`、`selectValueShow=false`、`handleClose`。
- 业务规则（含固定映射）：
  - 数量单位映射：`selectDataTypeValueOptions` = `[{label:"天",value:"0"},{label:"周",value:"1"},{label:"月",value:"2"},{label:"年",value:"3"}]`。
  - 保存文案：`valueUnit = (setValue !== 1) ? \` ${setValue} \` : ""`；`valueText = \`每${valueUnit}${label}\``；当类型为周（`"1"`）时追加 `"，" + selectWeeksValue.map(w => \`星期${w}\`).join("，")`。故：`1周` → `每周，星期X`；`2天` → `每 2 天`；`2周` → `每 2 周，星期一，星期三`……星期拼写在 `.map` 中直接使用 `week` 值本身（`weeksOptions` 的 `week` 已是中文「一」等）。
  - 保存按钮禁用规则：`selectDataTypeValue === '1' && selectWeeksValue.length === 0`（仅每周必须有至少一个星期；天/月/年不校验）。
  - 每周快捷项使用当前星期：`weekIndex = date.getDay()`，`num_to_chinese` = `["日","一","二","三","四","五","六"]`。
  - 硬性存储映射（`store/modules/task.js`，跨组件契约）：
    - `_eachTextObj = {每天:"0", 每周:"1", 每月:"2", 每年:"3"}`（`formatTaskObj` 用于 `eachText → taskCycle`）。
    - `_eachTextObjOne = {0:"每天",1:"每周",2:"每月",3:"每年"}`（`formatServerObj` 用于 `taskCycle → eachText`）。
    - `formatTaskObj`：`taskObj.taskCycle = _eachTextObj[eachText] || ''`；若 `eachText.includes("每周")`，则 `_arr = eachText.split("，")`，`taskCycle = _eachTextObj[_arr[0]]`，`remarkJsonObj.eachTextWeek = _arr[1]`；否则 `remarkJsonObj.eachTextWeek = ""`。
    - `formatServerObj`：`eachText = _eachTextObjOne[taskCycle]`；且当 `taskCycle === '1'` 时覆盖为 `每周 ${remarkJsonObj.eachTextWeek}`（**注意：此处用空格而非中文逗号，与前端产出的「每周，星期X」不一致**）。
  - 提交对象注释：`addTaskBlock.vue` L162 标注 `eachText // 任务周期 （每天_0，每周_1，每月_2，每年_3）`，与上述映射一致。
- 边界与已知缺陷：
  - 「自定义」菜单项被注释（L74-83），`case "自定义"` 分支不通过 UI 可达，导致自定义数量/星期选择功能实际不可用（**重写必须恢复入口或移除死代码**）。
  - `el-input type="number" min="1"` 的 `min` 仅约束步进器，手输 `0`/负数/小数不被 JS 校验，会生成 `每 0 天` / `每 -1 天` 等非法 `eachText`（**重写需做数值校验**）。
  - 前后端星期文案不对称：前端存 `remarkJsonObj.eachTextWeek = "星期二"`（带「星期」前缀），后端回显拼成 `每周 星期二`（空格）；`BbRepeatSelect` 需对此做兼容归一化。**待确认：后端实际存储/返回的 `eachTextWeek` 精确格式。**
  - 未使用死代码：`formatDateStr`、`getNextWeekOne`、`fomatDateFun` 导入及 `month/day/nextWeekIndex` 等（L178-182、L294-332）（**重写清理**）。
  - 周选项顺序为 一..日（`value 1..6,0`），但快捷项用 `getDay()` 0=日，需注意两处映射一致性。
  - `closeDropdown` 重置后 `el-input` 的值/`el-select` 面板态依赖组件内部实现，跨打开可能残留 UI 态（**重写用受控状态**）。
- 验收用例：
  1. 点击「每天」→ `dataText === "每天"` 且触发 `confirm`，下拉关闭（对应 `taskCycle="0"`）。
  2. 点击「每周」→ `dataText === "每周，星期<当前星期中文>"`，提交后 `taskCycle="1"` 且 `remarkJsonObj.eachTextWeek === "星期<当前星期中文>"`。
  3. 自定义：数量 2、单位「天」→ 保存 `dataText === "每 2 天"`；数量 1、单位「周」、勾选周一+周三 → `dataText === "每周，星期一，星期三"`（`taskCycle="1"`）。
  4. 单位选「周」且未勾选任一星期时「保存」按钮为禁用；勾选后恢复可点击。
  5. 已有重复（`dataText` 非空）且 `dataType="1"` 时显示红字「从不重复」，点击后 `dataText === ""` 且触发 `confirm`。
  6. 打开下拉并切到自定义面板后，关闭再打开应回到快捷菜单且状态复位（数量=1、单位=天、无勾选）。
