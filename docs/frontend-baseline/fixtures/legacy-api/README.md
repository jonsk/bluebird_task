# Legacy API Fixtures（旧接口形态）

> 用途：让**旧前端**在 Playwright 下**确定性渲染**，用于 `../screenshots/` 黄金截图与录屏（`03 §2.3.2`）。
> 形态来源：2026-09-28 从线上旧系统（`<旧系统地址>:8081`）Playwright 抓包所得的真实响应结构；**任务正文为合成数据**（线上库任务为空）。

## 文件

- `dataset.json` — 单一数据集，含：
  - `meta`：当前用户、约定说明；
  - `endpoints[]`：`{name, method, path, matchQuery?, body}`，body 支持 `"@task:101"` 等**引用**（`tasks` 表展开）；
  - `tasks{}`：任务对象字典（旧接口原始字段）。

## 旧接口形态要点（供重写逐字段对照）

| 接口 | 方法 | 形态 |
|---|---|---|
| `/api-server/api/user/login` | POST | `{ "token": "..." }` |
| `/api-server/admin/user/myself` | GET | 用户对象（`id,username,name,roles,auths,...`） |
| `/api-server/wechat/getWeChat` | GET | `{corpId,corpSecret,agentId}` |
| `/api-server/task/record/{day,week,join,collect}` | GET | MP 分页 `{records,total,size,current,pages}`，按 `completeStatus=0/1` 调用两次 |
| `/api-server/task/record/{do,link}` | GET | **裸数组** `[...]` |
| `/api-server/task/record/count` | GET | `{all,week,join,do,day,collect}` |
| `/api-server/task/menu/getmenulist` | GET | 自定义栏数组 |
| `/api-server/sysTag/page` | GET | 标签数组 `[{id,tagName,belongUserId}]` |
| `/api-server/sys/category/tree` | GET | 分类树 |
| `/api-server/task/record/getproxycalendar` | GET | 日历数组 |

### 任务对象（`tasks{}` 中每一项）关键字段

`id, taskContent, completeStatus("0"/"1"), taskAlarmTime（到期）, taskReminderTime（提醒）, taskCycle(""/"0"每天/"1"每周/"2"每月/"3"每年), taskNotes, taskSetupTime, isCollected("1"/"0"), belongUserId, belongUserName, total, completedtotal, tagList[{id,tagName}], executeUserList[{id,name}], taskFileList[], remarkJson(string), children[]`

> 前端 `store/modules/task.js#formatServerObj` 负责将其映射为卡片展示字段（`dateText/tipsText/eachText/taskTypeNames/userNameList/isImportant` 等）。

## 合成 vs 真实（修订 0303/N3）

- **结构/端点/字段**：来自**真实抓包**，与线上系统 100% 吻合（含 snowflake ID 逐字符一致，见 `0302/0303 审核`）。
- **数据值**：线上任务库为空（`count` 全 0、列表 `records:[]`），故 `dataset.json` 的任务正文与计数为**合成数据**（8 条任务、count 非 0），以便黄金截图有内容可渲染。此为**已声明的 SYNTHETIC，非缺陷**。
- 结论：结构与真实一致、数值为合成——正是"确定性渲染"所需；不得据此推断线上业务数据。

## 安全说明（重要）

- **线上真实响应含敏感值**：`/wechat/getWeChat` 返回明文 `corpSecret`、`/admin/user/myself` 返回密码哈希。本目录 fixtures 中这些字段一律**置空或合成**，**禁止**回填真实值。
- 未匹配的 `/api-server/**` 由 harness 返回空数组，**不访问线上后端**，避免拉取真实数据。

## 与新接口 fixtures 的关系

- 本目录 = **旧接口形态**（旧前端消费，用于黄金截图）。
- `../api/` = **新接口形态**（openapi.yaml，MSW 用）。
- 二者表达**同一组逻辑数据**（同一批任务/用户/标签）；差异见 `../README.md`。
