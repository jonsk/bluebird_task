-- 2026-10-27 工作日志第三轮减法：标签功能整块下线。
-- 标签只在日志里有筛选 / 编辑 / facet 三类入口，任务模块的 tag / task_tag 不受影响。
-- 历史关联数据（真实库 31 行）已于升级前导出：backups/note_tag_backup_20261027.csv
-- 统计视图 v_tag_relation 同步收敛为仅 TASK 分支。
DROP INDEX IF EXISTS idx_nt_note;
DROP INDEX IF EXISTS idx_nt_tag;
DROP TABLE IF EXISTS note_tag;

DROP VIEW IF EXISTS v_tag_relation;
CREATE VIEW v_tag_relation AS
SELECT 'TASK' AS target_type, tt.task_id AS target_id, tt.tag_id
FROM task_tag tt;
