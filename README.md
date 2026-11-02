# BlueBird Task

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Vue](https://img.shields.io/badge/Vue-3-42b883)

一个自托管的团队任务与工作日志管理系统。后端基于 Spring Boot 3，前端基于 Vue 3，
使用 SQLite 作为嵌入式数据库，开箱即用，无需额外安装数据库或缓存服务。

## 功能特性

- **任务管理**：创建、编辑、完成、收藏、指派、优先级、标签、截止时间与提醒
- **重复任务**：按规则计算展开，支持日历视图按实例查看
- **子任务**：在任务详情中直接添加步骤
- **自定义任务栏**：按项目或场景组织任务，支持重命名与删除
- **日历**：任务与工作日志的月视图，支持按天查看
- **工作日志**：每日记录、附件上传、下级日志查看、日志日历
- **回收站**：任务与日志统一回收、恢复
- **权限与组织**：管理员 / 用户管理员 / 审计员 / 普通成员四种角色，部门树与数据可见范围控制
- **审计日志**：登录与关键操作留痕，支持查询
- **认证方式**：本地账号密码；可选 OIDC（Casdoor）与企业微信登录
- **界面**：浅色 / 深色主题，桌面端布局

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3、Spring Security、MyBatis-Plus、Flyway |
| 数据库 | SQLite（嵌入式单文件，无需独立服务） |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Tailwind CSS |
| 测试 | JUnit 5、Vitest、Playwright |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+、pnpm 12+

### 1. 启动后端

```bash
cd backend

# 首次启动时创建管理员账号（用户名默认 admin，密码取此变量）
export BOOTSTRAP_ADMIN_PASSWORD='ChangeMe123'

mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`，数据库文件自动创建在 `backend/data/bluebird.db`。

> Windows PowerShell 使用 `$env:BOOTSTRAP_ADMIN_PASSWORD="ChangeMe123"`。
> 若未设置该变量，也可以在空库状态下调用 `POST /api/v1/bootstrap/admin` 创建首个管理员。
> 管理员首次登录后需要修改密码。

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173`。开发环境默认启用接口 Mock（MSW），无需后端即可浏览界面。

如需连接真实后端，创建 `frontend/.env.development.local`：

```dotenv
VITE_USE_MOCK=false
```

开发服务器会把 `/api` 代理到 `http://localhost:8080`。

## 构建与部署

前端产物会在打包时自动内嵌到后端，最终只产出一个可执行 Jar：

```bash
cd frontend
pnpm install
pnpm build

cd ../backend
mvn -DskipTests package

java -jar target/bluebird-task-0.1.0.jar
```

运行时数据默认保存在当前工作目录的 `data/` 下（SQLite 数据库与附件）。
`deploy/` 目录提供了单服务 docker-compose 模板，可按需构建镜像后使用。

## 配置

常用环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口 |
| `SQLITE_URL` | `jdbc:sqlite:./data/bluebird.db` | SQLite 连接串 |
| `STORAGE_PATH` | `./data/files` | 附件存储目录 |
| `JWT_SECRET` | 空 | 令牌签名密钥，生产环境务必设置 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 空 | 空库首次启动时创建的管理员密码 |
| `IDENTITY_PROVIDER` | `LOCAL` | 认证方式：`LOCAL` / `OIDC` / `WECHAT` |
| `ORGSYNC_MODE` | `NONE` | 组织同步方式：`NONE` / `SCIM` / `PULL` / `PUSH` |
| `LOG_DIR` | Jar 所在目录下的 `log/` | 运行日志目录 |
| `LOG_MAX_HISTORY` | `30` | 日志保留天数 |

## API 文档

- 契约文件：[`docs/api/openapi.yaml`](docs/api/openapi.yaml)
- 接口清单：[`docs/api/endpoints.md`](docs/api/endpoints.md)
- 开发环境启动后端后，可访问 Swagger UI：`http://localhost:8080/swagger-ui.html`

## 测试

```bash
# 后端
cd backend && mvn test

# 前端单元测试
cd frontend && pnpm test

# 前端端到端测试（Playwright，默认复用系统 Edge）
cd frontend && pnpm test:e2e
```

## 项目结构

```
.
├── backend/     # Spring Boot 服务（打包时内嵌前端产物）
├── frontend/    # Vue 3 前端
├── deploy/      # docker-compose 部署模板
├── docs/        # API 契约与项目文档
└── .github/     # CI 工作流
```

## 贡献

欢迎提交 Issue 与 Pull Request。

1. Fork 本仓库并创建分支
2. 完成改动并补充相应测试
3. 确保 `mvn test`、`pnpm test`、`pnpm test:e2e` 通过
4. 提交 Pull Request，说明改动内容与验证方式

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
