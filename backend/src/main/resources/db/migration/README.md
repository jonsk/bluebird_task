# 数据库迁移（Flyway）

## 铁律：已经执行过的迁移文件，一个字节都不能改

Flyway 对每个迁移文件算 CRC32 校验和（逐行 UTF-8 字节，不含换行符），存进 `flyway_schema_history`。
应用启动时会重新计算并比对，**哪怕只改一个注释、一个空格**，都会在启动阶段直接失败：

```
Migration checksum mismatch for migration version 1
-> Applied to database : -494698148
-> Resolved locally    : 262398819
```

这类失败发生在 `flywayInitializer` 建 bean 时，整个 ApplicationContext 起不来，表现为：
`UnsatisfiedDependencyException ... flywayInitializer ... Migrate failed: Validate failed`。

## 正确做法

- 改表结构 → 新增 `V{n}__描述.sql`，不要动旧的
- 改注释/格式 → 同样新增一个迁移，或直接放弃这个改动
- 已经改坏了（且只改了注释这类**不影响 schema** 的内容）→ 用 `git revert` 把文件还原成已应用时的内容

## 如果真的需要修历史

```bash
# 仅当确认变更不影响 schema 时：把 history 里的 checksum 更新为当前文件的
flyway -url=jdbc:sqlite:/path/to/bluebird.db -locations=filesystem:src/main/resources/db/migration repair
```

生产环境更稳妥的是还原文件，避免对已上线的库执行 repair。

## 当前基线

| 版本 | 文件 | 说明 |
| --- | --- | --- |
| V1 | `V1__init.sql` | 初始 schema（SQLite），已上线，**只读** |
