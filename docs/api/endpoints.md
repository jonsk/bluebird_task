# API 端点总览（自动生成）

> 本文由 `scripts/gen-endpoints-md.cjs` 从 `docs/api/openapi.yaml` 生成，**请勿手工编辑**。
> 它是 `scripts/check-contract.mjs` 的文档侧来源之一：openapi 增删端点后重新生成，否则门禁会红。

| 方法 | 路径 | 模块 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/admin/config | 后台配置 | 全部配置分组（ADMIN；密文只返回掩码） |
| POST | /api/v1/admin/config/casdoor/test | 后台配置 | 测试 Casdoor 连通性（ADMIN；拉 .well-known/openid-configuration） |
| GET | /api/v1/admin/config/{group} | 后台配置 | 单个配置分组（ADMIN） |
| PUT | /api/v1/admin/config/{group} | 后台配置 | 保存配置分组（ADMIN；值为 null/掩码视为不修改，空串表示清空） |
| GET | /api/v1/audit/logins | 审计 | 登录/退出日志分页（ADMIN / AUDITOR） |
| GET | /api/v1/audit/operates | 审计 | 操作日志分页（ADMIN / AUDITOR） |
| GET | /api/v1/auth/external/config | 认证 | 当前认证提供方配置（前端据此渲染登录入口） |
| POST | /api/v1/auth/login | 认证 | 账号密码登录 |
| POST | /api/v1/auth/logout | 认证 | 登出 |
| GET | /api/v1/auth/oidc/callback | 认证 | Casdoor 回调（浏览器整页跳转，非 XHR） |
| GET | /api/v1/auth/oidc/redirect | 认证 | 发起 Casdoor 单点登录（302 跳转上游 authorize） |
| POST | /api/v1/auth/refresh | 认证 | 刷新 token（匿名，凭 refresh token） |
| POST | /api/v1/bootstrap/admin | 认证 | 一次性引导创建首 ADMIN（仅空库可用，R25） |
| GET | /api/v1/departments | 组织 | 部门树 |
| POST | /api/v1/departments | 组织 | 新增部门（ADMIN） |
| GET | /api/v1/departments/export | 组织 | 导出部门（.xlsx，ADMIN/USER_MANAGER） |
| POST | /api/v1/departments/import | 组织 | 导入部门（.xlsx，ADMIN/USER_MANAGER） |
| GET | /api/v1/departments/import-template | 组织 | 下载部门导入模板（.xlsx） |
| PUT | /api/v1/departments/{id} | 组织 | 修改部门（含 `leaderId`，ADMIN） |
| DELETE | /api/v1/departments/{id} | 组织 | 删除部门（有子级/有成员则拒绝，ADMIN） |
| POST | /api/v1/files | 文件 | 通用上传（note 附件前置；`taskId` 非空 → 400，task 附件走 /tasks/{taskId}/attachments） |
| GET | /api/v1/files/config | 文件 | 上传约束（大小/扩展名白名单，前端预校验用） |
| GET | /api/v1/files/{id} | 文件 | 通用下载（登录 + 附件访问权：task 引用走任务可见，note 引用走日志可见，无引用仅创建者/ADMIN） |
| DELETE | /api/v1/files/{id} | 文件 | 删除附件（引用保护：仍有 task/note 引用 → 409） |
| GET | /api/v1/files/{id}/preview | 文件 | 预览（与下载同权限；后端按 id 代理，修 SSRF） |
| GET | /api/v1/jobs/logs | 调度 | 调度任务日志分页（ADMIN） |
| POST | /api/v1/jobs/{name}/run | 调度 | 手动重跑调度任务（ADMIN；`sys_job_log` 记录，0306/Q6） |
| GET | /api/v1/menus | menu | 我的自定义栏 |
| POST | /api/v1/menus | menu | 新增栏 |
| PUT | /api/v1/menus/{id} | menu | 改栏 |
| DELETE | /api/v1/menus/{id} | menu | 删栏 |
| POST | /api/v1/menus/{id}/items | menu | 添加任务到栏 |
| DELETE | /api/v1/menus/{id}/items/{itemId} | menu | 从栏移除任务 |
| GET | /api/v1/notes | 工作日志 | 工作日志分页（4 种 scope + 过滤/排序） |
| POST | /api/v1/notes | 工作日志 | 新建工作日志 |
| GET | /api/v1/notes/calendar | 工作日志 | 日历聚合（按日计数） |
| GET | /api/v1/notes/{id} | 工作日志 | 日志详情（含引用/被引/附件/字段级权限） |
| PUT | /api/v1/notes/{id} | 工作日志 | 修改日志（仅 owner/ADMIN；乐观锁 version） |
| DELETE | /api/v1/notes/{id} | 工作日志 | 删除日志（owner/ADMIN；逻辑删除并级联清理关联表） |
| GET | /api/v1/notes/{id}/attachments | 工作日志 | 附件列表（可见即可） |
| POST | /api/v1/notes/{id}/attachments | 工作日志 | 绑定附件（幂等；需写权限；仅允许本人上传且未被 task 引用的附件） |
| DELETE | /api/v1/notes/{id}/attachments/{attachmentId} | 工作日志 | 解除附件引用（保留文件；写权限） |
| GET | /api/v1/notes/{id}/attachments/{attachmentId}/download | 工作日志 | 附件下载（日志可见即可） |
| GET | /api/v1/notes/{id}/backlinks | 工作日志 | 被引用列表（谁引用了我） |
| GET | /api/v1/notes/{id}/relations | 工作日志 | 出向引用列表 |
| POST | /api/v1/notes/{id}/relations | 工作日志 | 新增引用（幂等；自引用 400；目标不可见 400/404） |
| DELETE | /api/v1/notes/{id}/relations/{relationId} | 工作日志 | 删除引用（创建者/源日志 owner/ADMIN） |
| POST | /api/v1/org/push | 组织 | 私有 IDM 推送（独立 SecurityFilterChain 校验 Bearer ORGSYNC_PUSH_TOKEN） |
| POST | /api/v1/org/sync | 组织 | 手动同步（PULL 模式，ADMIN） |
| GET | /api/v1/org/sync/latest | 组织 | 最近一次同步记录（ADMIN / AUDITOR / USER_MANAGER） |
| GET | /api/v1/org/sync/logs | 组织 | 组织同步日志分页（ADMIN / AUDITOR） |
| GET | /api/v1/org/sync/status | 组织 | 同步状态（ADMIN） |
| GET | /api/v1/scim/v2/Groups | scim | SCIM 查询部门（SCIM 模式；Group 按名称映射为 sys_department，02 §3.3） |
| POST | /api/v1/scim/v2/Groups | scim | SCIM 创建部门（SCIM 模式） |
| PUT | /api/v1/scim/v2/Groups | scim | SCIM 替换部门（SCIM 模式） |
| PATCH | /api/v1/scim/v2/Groups | scim | SCIM 局部更新部门（SCIM 模式） |
| DELETE | /api/v1/scim/v2/Groups | scim | SCIM 删除部门（SCIM 模式） |
| GET | /api/v1/scim/v2/Users | scim | SCIM 查询用户（`app.orgsync.mode=SCIM`；独立 SecurityFilterChain 校验 Bearer ORGSYNC_PUSH_TOKEN，ADR-008） |
| POST | /api/v1/scim/v2/Users | scim | SCIM 创建用户（SCIM 模式） |
| PUT | /api/v1/scim/v2/Users | scim | SCIM 替换用户（SCIM 模式） |
| PATCH | /api/v1/scim/v2/Users | scim | SCIM 局部更新用户（SCIM 模式） |
| DELETE | /api/v1/scim/v2/Users | scim | SCIM 删除用户（SCIM 模式） |
| GET | /api/v1/tags | 标签 | 我的标签 |
| POST | /api/v1/tags | 标签 | 新增标签 |
| PUT | /api/v1/tags/{id} | 标签 | 改标签 |
| DELETE | /api/v1/tags/{id} | 标签 | 删标签 |
| GET | /api/v1/tasks | 任务 | 任务列表（各视图） |
| POST | /api/v1/tasks | 任务 | 新建主任务 |
| GET | /api/v1/tasks/calendar | 任务 | 日历区间（有任务日期/实例） |
| GET | /api/v1/tasks/count | 任务 | 各视图计数（recurring 按展开实例） |
| GET | /api/v1/tasks/search | 任务 | 任务检索（note 引用选择器；仅返回当前用户可见任务，分页） |
| GET | /api/v1/tasks/subtasks | 任务 | 子任务列表 |
| GET | /api/v1/tasks/{id} | 任务 | 任务详情 |
| PUT | /api/v1/tasks/{id} | 任务 | 更新任务（须携带 `version`） |
| DELETE | /api/v1/tasks/{id} | 任务 | 软删除（不可逆：清空关联数据；见 R5） |
| POST | /api/v1/tasks/{id}/collect | 任务 | 收藏 |
| DELETE | /api/v1/tasks/{id}/collect | 任务 | 取消收藏 |
| POST | /api/v1/tasks/{id}/complete | 任务 | 完成任务（周期任务可传 dueAt 指定实例，见 R2；更新须携带 version） |
| POST | /api/v1/tasks/{id}/uncomplete | 任务 | 取消完成（周期任务可传 dueAt；更新须携带 version） |
| POST | /api/v1/tasks/{taskId}/attachments | 任务 | 上传并绑定任务附件（需任务写权限） |
| DELETE | /api/v1/tasks/{taskId}/attachments/{attachmentId} | 任务 | 解除并删除任务附件（需任务写权限；创建者或 ADMIN 可物理删除，否则仅解绑） |
| GET | /api/v1/tasks/{taskId}/attachments/{attachmentId}/download | 任务 | 任务附件下载（任务可见即可） |
| GET | /api/v1/tasks/{taskId}/attachments/{attachmentId}/preview | 任务 | 任务附件预览（任务可见即可） |
| GET | /api/v1/users | 用户 | 用户列表（人员选择器；手机号脱敏） |
| POST | /api/v1/users | 用户 | 新增用户（ADMIN） |
| GET | /api/v1/users/me | 用户 | 当前用户 |
| PUT | /api/v1/users/{id} | 用户 | 更新用户（ADMIN） |
| DELETE | /api/v1/users/{id} | 用户 | 禁用/删除用户（ADMIN） |
| PUT | /api/v1/users/{id}/password | 用户 | 修改密码（首登 must_change_password 引导） |
