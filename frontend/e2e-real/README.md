# e2e-real — 真实后端联调用例（选入式）

与 `../e2e/`（MSW fixtures）互补：这里的用例跑**真实 jar**，覆盖只有在真实后端才会暴露的问题。

## 为什么需要它

2026-09-30 的联调验收发现：默认 `pnpm test:e2e` 全部走 MSW 假数据（小 id、内存态），
因此**漏掉了 4 个阻断级缺陷**：

| 缺陷 | 现象 | MSW 为何测不出 |
|---|---|---|
| 主键超 JS 安全整数 | 雪花 id ≈2.1e18 > 2⁵³，浏览器 `JSON.parse` 静默改写末位 → 按 id 的操作全废（详情打不开、无法编辑/完成/删除） | fixture 用小 id（101/1201），永不超界 |
| 分类树 NPE | 任一「无分类任务」触发 `select(单可空列)` 返回 null 元素 → `GET /categories` 10000，左栏崩 | mock 直接返回 JSON，不走 MyBatis |
| 附件上传 10000 | `@MapperScan` 裸包扫描把 `FileStorage` 当 mapper → `BindingException` | mock 的 `POST /files` 恒成功 |
| 连续上传误判重复提交 | multipart 不进防重 key → 3 秒内第 2 个文件被 10005 拒绝 | mock 无防重拦截器 |

结论：**契约与交互链路必须至少有一遍跑真实后端**。

## 运行

```bash
# 1) 启动后端（在 jar 所在目录）
java -jar bluebird-task.jar --server.port=8080 \
  --app.bootstrap.admin-username=admin --app.bootstrap.admin-password=Admin123!

# 2) 跑联调用例
cd frontend
pnpm test:e2e:real              # API + UI
pnpm test:e2e:real -- api       # 只跑 API
pnpm test:e2e:real -- ui        # 只跑 UI
```

环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `E2E_REAL_BASE_URL` | `http://127.0.0.1:8080` | 后端地址 |
| `E2E_REAL_PASSWORD` | `Admin123!` | admin 口令 |
| `E2E_CHANNEL` | 本地 `msedge` / CI 随包 Chromium | 浏览器通道 |

## 用例范围

- `api.real.spec.ts`：登录/鉴权、**主键 JS 安全回归护栏**、任务与子任务全链路（含乐观锁 10006）、
  六视图与计数一致、附件上传（真实文件名/不限类型/可连发）与删除、系统默认部门与新建用户必填部门、
  手机号明文与邮箱读写清空、分类（个人/部门、部门分类须指定部门）、`/actuator/health` 与 SPA 静态资源。
- `ui.real.spec.ts`：登录与左栏、分类「个人」标签与部门分类建档、自定义栏删除与确认框居中、
  编辑器图标点开下拉、日历与编辑区不重叠、中文 locale、添加步骤/子任务状态切换、
  附件真实文件名与删除、组织管理（默认部门不可删/部门树选择/角色说明/邮箱列/禁用确认框居中）。

## 注意

- 用例会**写入数据**（任务/分类/用户/附件），并在结束时自行清理；请只对**测试环境**运行。
- 会改写系统默认部门的名称再改回，不改动其结构。
