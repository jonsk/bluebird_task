-- 移除「分类」功能（用户要求，2026-10-13）：删除 category 相关表与 task.category_id 列。
-- 标签（tag / task_tag）功能保留，不受影响。
-- 注：SQLite >= 3.35 支持 DROP COLUMN（本应用内置 3.46）。
-- V1/V2 仍会按历史顺序建表，本迁移在末尾把分类部分清掉，保证新旧库一致。
DROP INDEX IF EXISTS idx_cat_owner;
DROP INDEX IF EXISTS idx_cat_scope;
DROP TABLE IF EXISTS category;

ALTER TABLE task DROP COLUMN category_id;
