# Note 模块 §9 接口验收矩阵 · 执行脚本

对应 `docs/Note模块前端开发方案.md` §9（120 行用例，A–L 共 12 组；已脚本化 **117 例主跑 + 4 例开关关闭**，0 例未自动化）。

## 组成

| 文件 | 作用 |
| --- | --- |
| `matrix_runner.py` | 基础设施：HTTP 客户端、多账号登录缓存、`run()` 断言与结果收集、共享 fixture、测试授权行重置 |
| `matrix_cases_a.py` | A–C 组用例（Note CRUD / 列表与 scope / 置顶归档） |
| `matrix_cases_b.py` | D–L 组用例（授权 / 日历 / 附件 / files / task 附件 / 引用 / 搜索 / 标签 / facet） |
| `matrix_main.py` | 主跑入口（后端 `MANAGER_CAN_READ_SUBORDINATE=true`） |
| `matrix_toggle.py` | 开关关闭场景（后端 `MANAGER_CAN_READ_SUBORDINATE=false`） |
| `seed_accounts.py` | 建立/重置矩阵专用账号与测试授权行（幂等） |

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `BLUEBIRD_API` | `http://localhost:8080/api/v1` | 后端基址 |
| `BLUEBIRD_DB` | `../../backend/data/bluebird.db` | SQLite 路径，仅用于重置测试授权行 |
| `MATRIX_OUT` | `./matrix-out` | 结果 JSON 输出目录 |
| `MATRIX_PWD` | `Test@1234` | 验收账号统一密码 |

## 执行步骤

```powershell
cd scripts/acceptance
py seed_accounts.py                 # 1. 准备账号（幂等）
py matrix_main.py                   # 2. 主跑：期望 117 例全 PASS
# 3. 关开关重启后端后：
py matrix_toggle.py                 # 4. 期望 4 例全 PASS
```

后端启动：

```powershell
cd backend
$env:MANAGER_CAN_READ_SUBORDINATE='true'    # toggle 场景改为 false
mvn -o -q -DskipTests spring-boot:run
```

## 账号与 fixture

基础账号（由项目本地种子脚本建立，密码 `Test@1234`）：
`zhangli`（技术部负责人）、`lisi`（前端组）、`wangwu`（后端组）、`auditor`（AUDITOR）、`outsider`（市场部）、`useradmin`（USER_MANAGER）。

矩阵专用账号（`seed_accounts.py` 建立）：`noteadmin`（ADMIN）、`nodept`（无部门）、`mtxviewer`（纯 VIEWER）、`mtxsub1`/`mtxsub2`（前端组下属，用于 SUPERIOR PENDING 流程）。

`#19`（每 grantor 最多 5 条 PENDING）专用：`mtxcap`（被授权人）+ `mtxcapmgr1..6`（上级），
配套 6 级部门链 `MTX-上限-L1..L6`（L1 为根、逐级为 L2..L6 的父部门，负责人依次为 6 个上级），
因此 6 个上级都把 `mtxcap` 视为下属；前 5 条 SUPERIOR PENDING 成功，第 6 条期望 **400/10001**。

每次运行会写入 `MTX-*` 前缀的日志/任务/标签数据，并重置上述 5 个专用账号的授权行。

## 结果

`matrix-out/matrix_result_main.json`、`matrix-out/matrix_result_toggle.json`。
退出码：全部通过 0，否则 1（便于接入 CI）。

## 已知未自动化

- **0 例**。`#19` 已用「六级部门链 + 6 个上级账号」构造并纳入主跑。
- 契约偏差（归档 404、tag 200/10004、`GET /files/{id}` 文件流、task 不可写 404）已在 §9 表格与 §9.2 中按实现修正。
