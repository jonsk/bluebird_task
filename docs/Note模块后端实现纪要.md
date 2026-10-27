# Note 模块后端实现纪要

> 对应方案：`docs/Note模块后端开发方案.md`（下文简称「方案」）。
> 本文只记录**落地结果**与**与方案的偏差**，方案正文不重复。
> **2026-10-26 两轮减法后**：归档、visibility、探索/本部门、授权日志均已整块下线，方案正文为历史设计稿，本文为现行口径。

## 1. 交付清单（现状）

| 层 | 内容 |
| --- | --- |
| 迁移 | `V6__note.sql`（建表）、`V7__note_simplify.sql`（去 archived/visibility，scope 7→5）、`V8__drop_note_user_grant.sql`（删授权表） |
| 表 | `note` / `note_tag` / `note_attachment_ref` / `note_relation` + 索引 + `v_tag_relation` 视图（`note_user_grant` 已 DROP） |
| 领域 | `modules/note/domain/*`（`Note`、`NoteTag`、`NoteAttachmentRef`、`NoteRelation`） |
| 基础设施 | `modules/note/infrastructure/*Mapper`（4 个 MyBatis-Plus Mapper）、`util/NoteVisibilitySqlBuilder` |
| 应用 | `NoteAuthService`、`NoteQueryService`、`NoteCommandService`、`NoteRelationService`、`NoteAttachmentService`、`NoteAttachmentGuard`、`NoteTagCleaner` |
| 接口 | `NoteController`（含 4 个 scope 列表）、`NoteCalendarController`、`NoteRelationController`、`NoteAttachmentController`（共 30 个 note 相关端点） |
| 公共能力 | `common/spi/TagUsageCleaner`、`common/spi/AttachmentReferenceGuard`、`modules/org/application/CurrentUserDeptService`、`DepartmentPolicy` |
| file 改造 | `FileVO`/`FileService`/`FileController`/`AttachmentAccessService`、`TaskAttachmentController` + `TaskAttachmentGuard` |
| task 改造 | `TaskAuthService.isVisible`、`TaskQueryService.search`、`GET /tasks/search` |
| identity 改造 | `UserDTO.managerCanReadSubordinate`、`UserService.subordinatePage`、`GET /users?scope=subordinate` |
| 契约 | `docs/api/openapi.yaml`（64 paths / 92 ops；note 授权 6 个 op 已删除） |

## 2. 与方案的偏差（已实现，需知悉）

1. **HTTP 状态码不再恒 200**（方案 §1.3 时代的「HTTP 恒 200」已放宽）：
   - 未认证 → `401`；无权限 → `403`；资源不存在 / 不可见 → `404`；参数或请求体校验失败 → `400`；冲突 → `409`。
   - body 仍为 `ApiResult{code,message,traceId}`，`code` 保留业务语义。
   - 未映射的 API 路径统一 **HTTP 200 + code `10004`**（如已下线的 `/note-grants`、`/notes/{id}/archive`）。
2. **`note.log_date` 实体层为 `String`（`YYYY-MM-DD`）**，DTO 用 `LocalDate` 严格解析，非法日期 → `400`。
3. **幂等端点移除了 `@RepeatSubmit`**：`POST /notes/{id}/relations`、`POST /notes/{id}/attachments`。否则重复提交被 `10005` 拦截，破坏「幂等返回既有引用」的语义。
4. **`v_tag_relation` 只过滤 `deleted=0`**，不含权限过滤；标签 facet 必须叠加 `NoteVisibilitySqlBuilder` 的谓词，不得直接信任视图。
5. **`/users?scope=subordinate`**：命中该 scope 时忽略其余过滤参数，直接返回下属分页，供「员工日志」使用。
6. **`/files` 通用上传拒绝 `taskId`**：`taskId != null` → `400`（`TASK_ATTACHMENT_FORBIDDEN`）；任务附件只走 `/tasks/{taskId}/attachments`。
7. **附件删除是引用保护而非条件删除**：仍有 task/note 引用时 `DELETE /files/{id}` → `409`；`DELETE /notes/{id}/attachments/{attachmentId}` 只解绑、不删文件。
8. **`NoteVO.grantRole` 已改名 `role`**（取值仅 `OWNER` / `ADMIN` / `null`）；`fieldPermissions` 只剩「全 true / 全 false」两态。

## 3. 权限口径速查（二轮减法后）

| 动作 | 允许方 |
| --- | --- |
| 读（列表/详情/附件/引用/被引/日历） | `NoteAuthService.isVisible`：**自己 ∨（上级 ∧ `managerCanReadSubordinate`）∨ ADMIN** |
| 写（改/引用增删/附件绑定解绑/置顶） | **owner / ADMIN**；可见但非 owner（上级视角）→ `403` |
| 管理（删） | owner / ADMIN |
| 附件物理删除 | 创建者或 ADMIN，且无任何引用 |
| 列表 scope | `mine` / `subordinate`（开关关时 403+`50016`）/ `visible` / `all`（仅 ADMIN）；`shared` 等已下线 → `400`+`10001` |

## 4. 测试

| 测试 | 覆盖 |
| --- | --- |
| `NoteAuthMatrixTest` | 可见性矩阵（owner / 无关 / 同部门 / 上级开关开-关 / 下属反向 / ADMIN / deleted） |
| `NoteFlowTest` | 建/改/删、scope 校验、置顶、引用与回链、附件上传-绑定-下载-引用保护-解绑、下属可见、标签 facet、日历、乐观锁、已下线端点 `10004` |
| `NoteSubFlowTest` | 授权下线后的跨人可见性、附件绑定矩阵、文件删除守卫、引用可见性、标签级联、facet 与参数校验、日历参数 |
| `PermissionFieldTest` | `fieldPermissions` 三身份（owner/ADMIN/上级只读）+ task 详情 404 |

运行：

```powershell
mvn -o test                      # 全量
mvn -o test -Dtest=NoteFlowTest  # 单测
```

当前全量：**Tests run: 41, Failures: 0, Errors: 0**（BUILD SUCCESS）。

## 5. 二轮减法记录（2026-10-26）

| 项 | 处理 |
| --- | --- |
| 端点 | `/api/v1/note-grants` 6 个（list/create/approve/reject/upgrade/revoke）删除 |
| 表 | `note_user_grant` 由 `V8` DROP（升级前 31 行：5 条 APPROVED / 26 条 REVOKED，历史授权不可恢复） |
| 模型 | `NoteUserGrant*` 6 个类删除；`NoteAuthService` 去 grant 分支，`fieldPermissions` 只剩两态；`NoteVisibilitySqlBuilder.SCOPES` 4 值 |
| 错误码 | 50007 / 50008 / 50009 / 50010 / 50011 / 50013 / 50014 / 50015 删除 |
| 前端 | 被授权日志 / 授权管理两页、`NoteGrantForm`、`useNoteGrants` 删除；左栏只剩「我的 / 员工」 |
| 测试 | 后端 41（原 42）、单测 103（原 110）、e2e 62（原 63）、矩阵 105（原 119）+ 开关组 4 |

## 6. 后续可做

- 标签 facet 的 `name` 口径（非 mine scope）依赖 `NoteVisibilitySqlBuilder`，新增 scope 时两处必须同步。
- `/admin/notes` **仅 `ADMIN`**（2026-10-26 决策：全部日志只有管理员可见，`AUDITOR` 不加工作日志入口；e2e `P-05a` 守住直达 403）。
