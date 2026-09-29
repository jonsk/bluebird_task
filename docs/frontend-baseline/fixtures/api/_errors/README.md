# 错误态 fixtures（`_errors/`）

> 供**新前端 MSW** 的错误分支使用（`fixtures/api/**` 的成功态样例的补充）。依据后端 `02 §1.4 ErrorCode`。

## 用法

- 每个文件是完整的 `ApiResult` 错误体，`code != 0`（新前端 `http.ts` 响应拦截据 `code` 抛业务错误）。
- **HTTP 状态码不写进 JSON**：由 MSW handler 决定。约定：
  - **业务错误**（校验/权限/未找到等）→ **HTTP 200 + 本目录 JSON**（与后端 `GlobalExceptionHandler` 一致，见 `02 §1.3`）；
  - **传输层错误**（网关 502/网络中断）→ handler 直接 `HttpResponse.error()` 或 `status: 500` 空 body，无需 JSON。
- 复现旧前端「HTTP 500 → 静默」缺陷的对照证据见 `../../screenshots/README.md`（`state-httperr-*`）。

## 清单（对齐 `02 §1.4`，共 **22** 个非 0 码全覆盖 · 0304/P2 补齐 · 0306 增 3 码）

| 文件 | code | 含义 |
|---|---|---|
| `10000.system.json` | 10000 | SYSTEM_ERROR 系统异常（兜底） |
| `10001.param.json` | 10001 | PARAM_ERROR 参数校验失败 |
| `10002.unauthenticated.json` | 10002 | UNAUTHENTICATED 未登录 |
| `10003.forbidden.json` | 10003 | FORBIDDEN 无权限 |
| `10004.notfound.json` | 10004 | NOT_FOUND 资源不存在（含 API 404 规范，02 RF6） |
| `10005.repeat-submit.json` | 10005 | REPEAT_SUBMIT 重复提交（前端 UI 高频：防抖/双击） |
| `10006.version-conflict.json` | 10006 | VERSION_CONFLICT 并发版本冲突（乐观锁，0306/Q4，ADR-013） |
| `10007.too-many-requests.json` | 10007 | TOO_MANY_REQUESTS 请求过于频繁（限流/防暴破，0306/Q5） |
| `20001.user-not-found.json` | 20001 | USER_NOT_FOUND 用户不存在 |
| `20002.user-disabled.json` | 20002 | USER_DISABLED 用户被禁用 |
| `20003.bad-credentials.json` | 20003 | BAD_CREDENTIALS 用户名或密码错误 |
| `20004.no-permission-external.json` | 20004 | NO_PERMISSION_EXTERNAL 外部身份源无权限 |
| `20005.token-invalid.json` | 20005 | TOKEN_INVALID token 失效（触发 refresh/登出） |
| `20006.account-locked.json` | 20006 | ACCOUNT_LOCKED 账号已锁定（登录失败过多，0306/Q5） |
| `30001.task-not-found.json` | 30001 | TASK_NOT_FOUND 任务不存在 |
| `30002.task-deleted.json` | 30002 | TASK_DELETED 任务已删除 |
| `30003.task-already-completed.json` | 30003 | TASK_ALREADY_COMPLETED 任务已完成 |
| `30004.parent-not-found.json` | 30004 | PARENT_NOT_FOUND 父任务不存在 |
| `40001.file-not-found.json` | 40001 | FILE_NOT_FOUND 文件不存在 |
| `40002.file-too-large.json` | 40002 | FILE_TOO_LARGE 文件超限 |
| `40003.file-type-not-allow.json` | 40003 | FILE_TYPE_NOT_ALLOW 文件类型不允许 |
| `60001.sync-in-progress.json` | 60001 | SYNC_IN_PROGRESS 同步进行中（orgsync） |

> 覆盖口径：`02 §1.4` 除 `0 OK` 外的 **22** 个错误码已全部提供 fixture（0304/§4.2 盲区消除；0306 新增 `10006/10007/20006`）。
