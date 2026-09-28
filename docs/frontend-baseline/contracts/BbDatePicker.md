# 行为契约：dropdownSetDate.vue → BbDatePicker

> 源：`bluebird_task_Front/src/views/todolistModule/components/dropdownSetDate.vue`（已读源码 L1-278）

| 项 | 契约内容 |
|---|---|
| **用途** | 「新增/详情截止日期」选择器：下拉快捷项 + 自定义日期时间 + 删除截止日期 |
| **输入/状态来源** | `props.dataType`（默认 `"1"`）、`props.dataText`（当前截止日期展示文本，双向 `v-model:dataText`）；emit `update:dataText`、`confirm`；`date`/`selectDateValue`/`selectDataTimeValue` 本地状态 |
| **展示规则** | `el-dropdown`（`hide-on-click=false`，点击触发，L11-14）；默认显示快捷菜单（`listConfig`：今天/明天/下周 + 各项周几文本，L147-163）；「选择日期和时间」→ 切换为 `el-calendar`(280px) + `el-time-select`(01:00-23:30 步长 30min，L37-44) + 保存按钮；`dataType==='1' && dataText` 时有「删除截止日期」项（红字，L86-96） |
| **交互清单** | ① 点「今天」→ `update:dataText('今天 23:59:59')`+`confirm`+关（L169-173）；② 「明天」→ `明天 23:59:59`（L174-180）；③ 「下周」→ `getNextWeekOne()` 下周一 ` 23:59:59`（L182-188）；④ 「选择日期和时间」→ 显示日历+时间（L189-191）；⑤ 保存 → `选择日期 时间`（时间默认 `08:00`，L200-227）→ `update:dataText`+`confirm`；⑥ 「删除截止日期」→ `dataText=''`（L192-195）；⑦ 开关时 `changeDropdown`/`showDropdown`/`closeDropdown` 重置状态（L228-242） |
| **业务规则** | 快捷项时间固定 `23:59:59`；自定义默认时间 `08:00`；`num_to_chinese` 周日=日；`getNextWeekOne` 若今天周一则下周一=+7 天（L256-274） |
| **边界与已知缺陷** | ① 旧的整日历+保存（注释块 L22-31）与现「日历+时间」并存，隐藏分支死代码（重写仅保留当前生效路径）；② 长期注释的 `选择日期`（L66-75）不可用；③ `selectDateShow` 由「右键下拉项」控制，打开/关闭状态机易串扰（重写用显式模态） |
| **验收用例** | 1) 今天/明天/下周各设 `23:59:59`；2) 自定义日期+时间默认 08:00 可改；3) 已有截止日期时显示删除项、点击清空；4) `v-model:dataText` 双向；5) dropdown 开关状态复位 |

> 新组件 `BbDatePicker` 待导出；`dateText`→周/日语义映射见 `BbTaskComposer.md`。截图证据待旧前端隔离运行补充。
