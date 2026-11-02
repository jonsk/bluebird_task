-- 回收站（2026-10-30）
-- 语义变更：task / note 的「删除」只打逻辑删标记（deleted=1），并记录删除人与删除时间；
-- 原先的级联清理（参与人/标签/收藏/自定义栏/附件引用/引用关系/磁盘文件）延后到「彻底删除」。
-- 新增列允许为 NULL：历史已删行 deleted_at/deleted_by 为空，界面按「未知」展示（升级前删除，子数据可能已清理）。

ALTER TABLE task ADD COLUMN deleted_at TEXT;
ALTER TABLE task ADD COLUMN deleted_by INTEGER;

ALTER TABLE note ADD COLUMN deleted_at TEXT;
ALTER TABLE note ADD COLUMN deleted_by INTEGER;

-- 回收站列表：deleted=1 + 按删除人过滤 + 按删除时间倒序
CREATE INDEX IF NOT EXISTS idx_task_recycle ON task (deleted, deleted_by, deleted_at);
CREATE INDEX IF NOT EXISTS idx_note_recycle ON note (deleted, deleted_by, deleted_at);
