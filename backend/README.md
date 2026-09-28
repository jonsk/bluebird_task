# backend/ — 后端工程（Spring Boot 3 + Java 21 + MyBatis-Plus + PostgreSQL）

> **当前阶段：M0 占位。** 分层/模块/配置详见 `../Task/02后端模块详细设计.md`（审核中，冻结后复制进 `../docs/`）。代码随 M0 初始化开始，本目录暂为骨架。

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

- 依赖：`docker compose -f deploy/docker-compose.dev.yml up -d`（pg + redis）
- 构建单制品：见顶层 README / `../Task/01蓝鸟重构方案.md §11`
