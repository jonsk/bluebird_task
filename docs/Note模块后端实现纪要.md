# Note 模块后端实现纪要

> 对应方案：`docs/Note模块后端开发方案.md`（下文简称「方案」）。
> 本文只记录**落地结果**与**与方案的偏差**，方案正文不重复。

## 1. 交付清单

| 层 | 内容 |
| --- | --- |
| 迁移 | `backend/src/main/resources/db/migration/V6__note.sql`：`note` / `note_tag` / `note_user_grant` / `note_attachment_ref` / `note_relation` + 索引 + `v_tag_relation` 视图 |
| 领域 | `modules/note/domain/*`（`Note`、`Visibility`、`NoteTag`、`NoteUserGrant`、`NoteAttachmentRef`、`NoteRelation`） |
| 基础设施 | `modules/note/infrastructure/*Mapper`（5 个 MyBatis-Plus Mapper）、`util/NoteVisibilitySqlBuilder` |
| 应用 | `NoteAuthService`、`NoteQueryService`、`NoteCommandService`、`NoteUserGrantService`、`NoteRelationService`、`NoteAttachmentService`、`NoteAttachmentGuard`、`NoteTagCleaner` |
| 接口 | `NoteController`、`NoteCalendarController`、`NoteRelationController`、`NoteAttachmentController`、`NoteUserGrantController` |
| 公共能力 | `common/spi/TagUsageCleaner`、`common/spi/AttachmentReferenceGuard`、`modules/org/application/CurrentUserDeptService`、`DepartmentPolicy` |
| file 改造 | `FileVO`/`FileService`/`FileController`/`AttachmentAccessService`、`TaskAttachmentController` + `TaskAttachmentGuard` |
| task 改造 | `TaskAuthService.isVisible`、`TaskQueryService.search`、`GET /tasks/search` |
| identity 改造 | `UserDTO.managerCanReadSubordinate`、`UserService.subordinatePage`、`GET /users?scope=subordinate` |
| 契约 | `docs/api/openapi.yaml` 新增 note / 附件 / `/files/config` / `/tasks/search` 等定义 |

## 2. 与方案的偏差（已实现，需知悉）

1. **HTTP 状态码不再恒 200**（方案 §1.3 时代的「HTTP 恒 200」已放宽）：
   - 未认证 → `401`；无权限 → `403`；资源不存在 / 不可见 → `404`；参数或请求体校验失败 → `400`；冲突 → `409`。
   - body 仍为 `ApiResult{code,message,traceId}`，`code` 保留业务语义（`HttpStatusException` + `GlobalExceptionHandler`）。
   - 影响：`AuthSmokeTest`、`CasdoorIntegrationTest` 断言改为 `isUnauthorized()`。
2. **归档日志的读写口径**：
   - 非 owner/ADMIN **不可见**（方案 §3.3/§7）；普通读取一律 `404`。
   - 但已获 `EDITOR` 授权的用户执行写操作时，返回 **HTTP 200 + code `50013`**（「已归档日志不允许编辑」），
     而不是 `404`——与验收矩阵第 27a 项一致。
   - 实现：写路径先 `NoteAuthService.requireExisting`（只校验存在/未删除），再 `assertWritable`
     （不可见时若为归档 + EDITOR 则抛 `50013`，否则 `404`）。`GET` 路径仍走 `requireVisible`，保持 404。
3. **`note.log_date` 实体层为 `String`（`YYYY-MM-DD`）**，DTO 用 `LocalDate` 严格解析，非法日期 → `400`。
4. **幂等端点移除了 `@RepeatSubmit`**：`POST /notes/{id}/relations`、`POST /notes/{id}/attachments`。否则重复提交被 `10005` 拦截，破坏「幂等返回既有引用」的语义。
5. **`v_tag_relation` 只过滤 `deleted=0`**，不含归档/权限过滤；标签 facet 必须叠加 `NoteVisibilitySqlBuilder` 的谓词，不得直接信任视图。
6. **`/users?scope=subordinate`**：命中该 scope 时忽略其余过滤参数，直接返回下属分页，供日志授权与主管视图使用。
7. **`/files` 通用上传拒绝 `taskId`**：`taskId != null` → `400`（`TASK_ATTACHMENT_FORBIDDEN`）；任务附件只走 `/tasks/{taskId}/attachments`。
8. **附件删除是引用保护而非条件删除**：仍有 task/note 引用时 `DELETE /files/{id}` → `409`；`DELETE /notes/{id}/attachments/{attachmentId}` 只解绑、不删文件。

## 3. 权限口径速查

| 动作 | 允许方 |
| --- | --- |
| 读（列表/详情/附件/引用/被引/日历） | `NoteAuthService.isVisible`：owner / ADMIN / PUBLIC / 本部门 INTERNAL / 有效授权 VIEWER+ / 下属（开关开启）/ 授权人（SUPERIOR 已通过） |
| 写（改/引用增删/附件绑定解绑/置顶） | owner / ADMIN / 有效授权 EDITOR；归档后只读 |
| 管理（删/归档/取消归档/授权） | owner / ADMIN |
| 授权 | owner（`mode=OWNER`，立即生效）、主管（`mode=SUPERIOR`，需下属审批）、ADMIN（`mode=ADMIN`，立即生效） |
| 附件物理删除 | 创建者或 ADMIN，且无任何引用 |

## 4. 测试

| 测试 | 覆盖 |
| --- | --- |
| `NoteAuthMatrixTest` | 15 行可见性/权限矩阵 |
| `NoteFlowTest` | 建/改/删、7 scope、置顶归档、授权状态机、引用与回链、附件上传-绑定-下载-引用保护-解绑、下属可见、标签 facet、日历、归档 EDITOR `50013` |

运行：

```powershell
mvn -o test                      # 全量
mvn -o test -Dtest=NoteFlowTest  # 单测
```

当前全量：**Tests run: 31, Failures: 0, Errors: 0**。

## 5. 后续可做

- 标签 facet 的 `name` 口径（其他 scope）依赖 `NoteVisibilitySqlBuilder`，新增 scope 时两处必须同步。
