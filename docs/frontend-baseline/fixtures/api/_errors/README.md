# 错误态 fixtures（`_errors/`）

> 供**新前端 MSW** 的错误分支使用（`fixtures/api/**` 的成功态样例的补充）。依据后端 `02 §1.4 ErrorCode`。

## 用法

- 每个文件是完整的 `ApiResult` 错误体，`code != 0`（新前端 `http.ts` 响应拦截据 `code` 抛业务错误）。
- **HTTP 状态码不写进 JSON**：由 MSW handler 决定。约定：
  - **业务错误**（校验/权限/未找到等）→ **HTTP 200 + 本目录 JSON**（与后端 `GlobalExceptionHandler` 一致，见 `02 §1.3`）；
  - **传输层错误**（网关 502/网络中断）→ handler 直接 `HttpResponse.error()` 或 `status: 500` 空 body，无需 JSON。
- 复现旧前端「HTTP 500 → 静默」缺陷的对照证据见 `../../screenshots/README.md`（`state-httperr-*`）。

## 清单（对齐 `02 §1.4`）

| 文件 | code | 含义 |
|---|---|---|
| `10001.param.json` | 10001 | PARAM_ERROR 参数校验失败 |
| `10002.unauthenticated.json` | 10002 | UNAUTHENTICATED 未登录 |
| `10003.forbidden.json` | 10003 | FORBIDDEN 无权限 |
| `10004.notfound.json` | 10004 | NOT_FOUND 资源不存在（含 API 404 规范，02 RF6） |
| `20003.bad-credentials.json` | 20003 | BAD_CREDENTIALS 用户名或密码错误 |
| `20005.token-invalid.json` | 20005 | TOKEN_INVALID token 失效（触发 refresh/登出） |
| `30001.task-not-found.json` | 30001 | TASK_NOT_FOUND |
| `30003.task-already-completed.json` | 30003 | TASK_ALREADY_COMPLETED |
| `40002.file-too-large.json` | 40002 | FILE_TOO_LARGE |
