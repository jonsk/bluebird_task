# BlueBird Task — Backend

BlueBird Task 的服务端，基于 Spring Boot 3 构建，使用 SQLite 作为嵌入式数据库，
打包为单个可执行 Jar（前端产物在打包时一并内嵌）。

## 技术栈

- Java 21 / Spring Boot 3
- Spring Security + JWT
- MyBatis-Plus
- Flyway（数据库迁移）
- SQLite（单文件数据库，无需独立服务）
- springdoc-openapi（接口文档）

## 环境要求

- JDK 21+
- Maven 3.9+

## 本地运行

```bash
cd backend

# 空库首次启动时创建管理员（用户名默认 admin）
export BOOTSTRAP_ADMIN_PASSWORD='ChangeMe123'

mvn spring-boot:run
```

- 服务地址：`http://localhost:8080`
- 数据库文件：`backend/data/bluebird.db`（首次启动自动创建并执行迁移）
- 附件目录：`backend/data/files`

> Windows PowerShell 使用 `$env:BOOTSTRAP_ADMIN_PASSWORD="ChangeMe123"`。
> 未设置该变量时，也可在空库状态下调用 `POST /api/v1/bootstrap/admin` 创建首个管理员。

## 构建

前端产物会被自动内嵌到 Jar 中，构建前请先在 `frontend/` 执行 `pnpm build`：

```bash
cd frontend && pnpm install && pnpm build
cd ../backend && mvn -DskipTests package

java -jar target/bluebird-task-0.1.0.jar
```

`frontend/dist` 不存在时，打包会跳过前端资源，仅产出后端服务。

## 测试

```bash
mvn test
```

测试位于 `src/test/java`，覆盖认证、任务、工作日志、回收站等核心流程。

## 配置

默认配置见 `src/main/resources/application.yml`，常用环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口 |
| `SQLITE_URL` | `jdbc:sqlite:./data/bluebird.db` | 数据库连接串 |
| `STORAGE_PATH` | `./data/files` | 附件存储目录 |
| `JWT_SECRET` | 空 | 令牌签名密钥，生产环境务必设置 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 空 | 空库首次启动时创建的管理员密码 |
| `IDENTITY_PROVIDER` | `LOCAL` | 认证方式：`LOCAL` / `OIDC` / `WECHAT` |
| `ORGSYNC_MODE` | `NONE` | 组织同步方式 |
| `DOC_ENABLED` | `false`（开发环境为 `true`） | 是否开启 Swagger UI |
| `LOG_DIR` | Jar 所在目录下的 `log/` | 运行日志目录 |

更多配置项（存储大小限制、日志保留天数、OIDC 参数等）见 `application.yml` 中的注释。

## 代码结构

```
src/main/java/com/bbtc/bluebird/
├── common/       # 统一响应、异常、分页、MyBatis 扩展等基础设施
├── config/       # 安全、Web、Jackson、OpenAPI 等配置
└── modules/
    ├── identity/ # 登录认证、用户与角色
    ├── org/      # 部门与组织架构
    ├── task/     # 任务、标签、自定义任务栏
    ├── note/     # 工作日志与附件
    ├── file/     # 文件上传与下载
    ├── audit/    # 登录与操作审计
    ├── recycle/  # 回收站
    ├── job/      # 定时任务
    └── config/   # 运行时配置（如 OIDC 参数）
```

数据库迁移脚本位于 `src/main/resources/db/migration/`，应用启动时由 Flyway 自动执行。

## 接口文档

- 契约文件：[`docs/api/openapi.yaml`](../docs/api/openapi.yaml)
- 接口清单：[`docs/api/endpoints.md`](../docs/api/endpoints.md)
- 启动后端后访问 Swagger UI：`http://localhost:8080/swagger-ui.html`

## 相关文档

- [项目总览](../README.md)
