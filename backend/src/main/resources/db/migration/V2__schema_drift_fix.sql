-- 迁移 2：修正 V1 与实体/API 契约的列漂移（仅新增列，向后兼容；无数据迁移）
--
-- 背景：`category.updated_at` 已被 CategoryNodeDTO 与 `docs/api/openapi.yaml`（Category.updatedAt）
-- 暴露，但 V1 建表时漏建，导致 `GET /categories`（CategoryService.tree → selectList）直接
-- 报 `no such column: updated_at`。此处补列，不改动 V1（保护既有库的 Flyway 校验和）。

ALTER TABLE category ADD COLUMN updated_at TEXT;
