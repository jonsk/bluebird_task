# 行为契约：dropdownSetTips.vue → BbRemindSelect

> 源：`bluebird_task_Front/src/views/todolistModule/components/dropdownSetTips.vue`（已读源码 L1-339；行号为导出时快照，以方法名/类名/字段名为准）

- 用途：任务「提醒我」选择器。通过 `el-dropdown` 提供时段快捷项（今日晚些时候 / 明天 / 下周）、自定义日期时间（日历 + 时间下拉）以及删除提醒，最终产出 `tipsText`。
- 输入/状态来源：
  - props：`dataType: String = "1"`、`dataText: String = ""`（`v-model:dataText`）。
  - emits：`update:dataText`、`confirm`。调用方（`RightBoxDialog/index.vue` L135-139）以 `dataType="0"`、`v-model:dataText="taskStore.dialog_right_config_Obj.tipsText"`、`@confirm="updateRightItemHandler({type:'tipsText'})"` 接入。
  - 无 inject / store 直接依赖；仅工具函数 `fomatDateFun`（`@/utils/utils.js`，用于 `"day"`、`"dateObj"` 格式化）。
  - 本地状态：`dropdownRef`、`selectValueShow`、`selectDataValue = ref(new Date())`、`selectDataTimeValue = ref("")`、`date/listConfig`；下拉列表 `listConfig` 为 `reactive`。
- 展示规则：
  - `el-dropdown`：`hide-on-click=false`、`trigger="click"`、`popper-class="dropdownNotOverflow"`，`@visible-change="changeDropdown"`；默认插槽 `@click="showDropdown"` 手动 `handleOpen`。
  - `selectValueShow=false` 时显示快捷菜单 `listConfig` + 「选择日期和时间」+（条件）「删除提醒」：
    - 「今日晚些时候」`textTips = activeDateText`，图标 `Timer`。
    - 「明天」`textTips = 周${num_to_chinese(nextWeekIndex)}，9:00`，图标 `Timer`。
    - 「下周」`textTips = "周一，9:00"`，图标 `Timer`。
    - 「选择日期和时间」固定项，图标 `Timer`。
    - 「删除提醒」仅当 `dataType === '1' && dataText` 时显示，红字（`--font-color-warning`），图标 `Delete`。
  - `selectValueShow=true` 时显示自定义面板：`el-calendar`（`h-[280px]`）+ `el-time-select`（`start="01:00" step="00:30" end="23:30" clearable=false`）+「保存」按钮。
- 交互清单：
  - 「今日晚些时候」→ `update:dataText(${day} ${activeDateText ? activeDateText + ':00' : '23:59:59'})` → `confirm` → `closeDropdown`；异常/回滚：无。
  - 「明天」→ `update:dataText(${明天 day} 9:00:00)`（`sfStr = "9:00"`）→ `confirm` → 关闭。
  - 「下周」→ `getNextWeekOne()` 取下周一 `yyyy-MM-dd` → `update:dataText(${nextWeek} 9:00:00)` → `confirm` → 关闭。
  - 「选择日期和时间」→ `selectValueShow = true`（不关闭下拉，仅切换面板）。
  - 「保存」（`confirmSelectData`）→ `update:dataText(${fomatDateFun(selectDataValue,'day')} ${selectDataTimeValue}:59)` → `confirm` → `selectDataValue = new Date()` → 关闭。
  - 「删除提醒」→ `update:dataText("")` → 关闭。注意：**不触发 `confirm`**（与其他项不一致）。
  - 下拉 `visible-change=false` → `changeDropdown` → `closeDropdown`；`closeDropdown` 会重置 `selectDataValue = new Date()`、重算 `selectDataTimeValue`、`selectValueShow=false`、`handleClose`。
- 业务规则：
  - **提醒未启用提示（0306/#5，`03 §5.3.3`）**：一期 `remind_at` 仅**持久化 + 前端展示**，**不触发任何投递**（`TaskRemindService` 列二期，`01 §3/§7.7`）。UI **必须显式提示「提醒暂不启用（仅保存时间，不发送通知）」**：可在 `BbRemindSelect` 触发器旁加 info 文案或 `BbTooltip`，避免用户误以为会收到通知。重写时该提示随本组件一起实现。
  - 快捷时段阈值（setup 时一次性计算，`hours` 为当前小时）：`hours+1 < 12` → `activeDateText="12:00"`；`hours>=12 && hours+1<=16` → `"16:00"`；`hours>=16 && hours+1<=20` → `"20:00"`；其余为空。`activeDateText` 为空时「今日晚些时候」回退 `23:59:59`。
  - 自定义时间默认值：`selectDataTimeValue = ${hours+1>=24?'01':hours+1}:${minutes<30?'00':'30'}`。
  - 固定常量 `sfStr = "9:00"`（缺前导 0，最终拼接为 `9:00:00`）。
  - `num_to_chinese`：`["日","一","二","三","四","五","六"]`，`getDay()` 0=日。
  - `getNextWeekOne`：今天为周一（`getDay()===1`）则下周一 = +7 天；周日（0）按 `daysToAdd = 6+7`。
  - 保存时间精确到 `:59` 秒；快捷项秒数分别为 `:00`（明天/下周）与 `:59:59`（今日晚些时候回退）。
  - `dataText`→`tipsText` 语义：组件仅产出展示/提醒字符串，接口字段映射在 `task.js`（`formatTaskObj`：`tipsText → taskReminderTime`）。
- 边界与已知缺陷：
  - 阈值空档：当前小时为 20/21/22/23 时 `activeDateText` 为空，「今日晚些时候」退化为当天 `23:59:59`（合理但非显式设计），**重写建议显式化**。
  - 「删除提醒」不 emit `confirm`，在编辑弹窗场景可能不触发接口更新（旧 bug，**重写应统一触发变更事件**）。
  - `activeDateText`、`selectDataTimeValue` 默认值均在 setup 时基于闭包 `date` 计算且不随组件存活刷新（`closeDropdown` 用旧 `date` 重算），跨时段驻留会取到陈旧时间（**重写改为实时计算**）。
  - 大量注释死代码（旧版 `runHandler`/`confirmSelectData` 逻辑，L214-275）与 `sfStr`、`formatDateStr` 等未使用函数（L298-310），**重写仅保留生效路径**。
  - `el-dropdown` 点击外部依赖 `hide-on-click=false` + 手动 `handleOpen`，打开/关闭状态机脆弱（**重写用受控弹层**）。
- 验收用例：
  1. 无提醒时打开，仅见三个快捷项 + 「选择日期和时间」，不见「删除提醒」。
  2. 点击「明天」→ `dataText === "<明天日期> 9:00:00"` 且触发 `confirm`，下拉关闭。
  3. 点击「下周」→ `dataText` 为下周一日期 + ` 9:00:00`（今天为周一时验证 +7 天）。
  4. 「选择日期和时间」→ 面板切换为日历+时间；改日期时间后点「保存」→ `dataText === "<所选日期> <HH:mm>:59"` 且触发 `confirm`。
  5. 已有提醒（`dataText` 非空）且 `dataType="1"` 时显示红字「删除提醒」，点击后 `dataText === ""`（记录是否触发 `confirm` 的当前差异）。
  6. 打开后再关闭下拉，面板状态与默认时间被复位（再次打开为快捷菜单）。
  7. 选中任一提醒时间后，UI 旁出现「提醒暂不启用（仅保存时间，不发送通知）」提示（info 文案 / `BbTooltip`）；所选时间仍随表单保存到 `remindAt`（`0306/#5` / `03 §5.3.3`）。
