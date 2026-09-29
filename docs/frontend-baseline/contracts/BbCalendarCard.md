# 行为契约：CalendarCard.vue → BbCalendarCard

> 源：`bluebird_task_Front/src/views/todolistModule/components/CalendarCard.vue`（已读源码 L1-71；行号为导出时快照，以方法名/类名/字段名为准）

| 项 | 契约内容 |
|---|---|
| **用途** | 左栏「日历」卡片：展示当月日历，标注「有任务」日期，点击某日跳转全部任务视图并按日过滤 |
| **输入/状态来源** | `getCalendarTask()`（旧接口 `GET /task/record/getproxycalendar`，**非** `/calendar`；校正见 0304/D2；`record.js` L178）→ `activeDateArr`（有任务日期字符串数组，如 `'2026-10-01'`）；`taskStore.task_date`；`useRouter`；自身 `mainDateValue`（当前显示月） |
| **展示规则** | `el-calendar`（高度 280px，宽 350px，L36/53）；日期格仅显示 `MM-DD`（`data.day.split('-').slice(2).join('-')`，L40）；**有任务日期显示小圆点** `.hot`（5px 圆点灰 `#909399`，`showHow(day)=activeDateArr.includes(day)`，L42/L55-62）；日历格高 35px 居中（L65-69 `::v-deep`） |
| **交互清单** | 点击日期格 → `getCalendarDay(data)`（L21-30）：`taskStore.task_date = data.day` → `router.push({path:'/allTask', query:{date}})` → 清空搜索 `taskStore.search_task_content=''` → emit `date-selected` |
| **业务规则** | 只有 `allTask` 视图响应日期过滤（跳转固定 `/allTask?date=`）；`activeDateArr` 精确字符串匹配 |
| **边界与已知缺陷** | ① `getCalendarData()` 在 setup 即调用一次（L17），**切月不重新拉取**（含未来月/跨月数据的完整日期集可能不全，重写需按月在窗口内拉取 `02 §4.4` 日历区间口径）；② 无 loading/错误态（重写补）；③ `activeDateArr.includes(date)` 用当天全天字符串，与后端 `due_at` 时区需对齐（GMT+8，02 jackson） |
| **验收用例** | 1) 有任务日期显示圆点；2) 点击日期 → 跳 `/allTask?date=` 且搜索清空；3) 切月后圆点数据正确刷新；4) 点击无任务日期不显示圆点 |

> 新语义：日历区间走 `GET /tasks/calendar?start=&end=`（02 §4.5），按窗口计算；recurring 按展开实例（02 §4.4）。截图/录屏证据已采集（见 `../screenshots/README.md`；按策略**不入库**，由 `../../scripts/golden-capture/` 复现）。
