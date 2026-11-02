-- 2026-11-02 工作日志第五轮减法：引用 / 被引用功能整块下线。
-- 前端不再展示引用选择器与引用/被引用列表，后端 /notes/{id}/relations、
-- /notes/{id}/backlinks 端点同步摘除；历史引用关系数据随表一并清理。
DROP INDEX IF EXISTS idx_nr_source;
DROP INDEX IF EXISTS idx_nr_target;
DROP TABLE IF EXISTS note_relation;
