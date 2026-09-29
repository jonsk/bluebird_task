# backend/ — 后端工程（Spring Boot 3 + Java 21 + MyBatis-Plus + SQLite）

> **当前阶段：M0/M1 工程骨架已落地。** 分层/模块/配置详见 `../Task/02后端模块详细设计.md`（审核中，冻结后复制进 `../docs/`）。
>
> 已实现：Maven 工程 + 单制品内嵌 `frontend/dist`（ADR-009）；common（ApiResult/ErrorCode/异常/分页/BaseEntity/traceId/雪花ID）；config（Jackson `+08:00`、MyBatis-Plus 乐观锁/分页、SQLite PRAGMA、逻辑删除防护、配置释放 ADR-010）；security（无状态 JWT + 安全头 + 401/403）；identity（登录/刷新/改密/首 ADMIN 种子）+ audit 登录日志；Flyway `V1__init.sql`（全量 DDL，ADR-016）。冒烟测试见 `src/test/java/.../AuthSmokeTest.java`。
>
> 验证：`mvn test`（3/3 通过）、`mvn -DskipTests package`（jar 内含 `static/`）。

## 规划结构（对应 02 §1.2 / 01 §6）

```
backend/
├── pom.xml                    # Maven；打包期用 maven-resources-plugin 内嵌 frontend/dist → static/
├── Dockerfile                 # 多阶段（先 pnpm 构建前端 → 内嵌 dist → mvn package）
└── src/main/
    ├── java/com/bbtc/bluebird/
    │   ├── BlueBirdApplication.java
    │   ├── common/            # ApiResult/异常/BaseEntity/工具
    │   ├── config/            # security/mybatis/web/openapi
    │   └── modules/identity,org,task,file,audit/
    └── resources/
        ├── application.yml / -dev / -prod
        ├── db/migration/V1__init.sql
        ├── mapper/
        └── static/            # 打包期由 frontend/dist 注入（不入 git，ADR-009）
```

## 入口命令（占位，随 M0 补充）

- 依赖：**无外部依赖**（SQLite 嵌入式、无缓存，ADR-016）；直接 `cd backend && mvn spring-boot:run` 即可（数据库自建 `./data/bluebird.db`）
- 构建单制品：见顶层 README / `../Task/01蓝鸟重构方案.md §11`
