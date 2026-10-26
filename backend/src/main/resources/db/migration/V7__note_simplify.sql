-- 工作日志做减法（V7__note_simplify.sql）
-- 1) 归档能力下线：archived 列保留（SQLite 删列代价高），数据一律归一为 0，业务不再读写。
-- 2) 可见性收敛为「仅自己 + 被授权者」：visibility 列保留，数据归一为 PRIVATE，业务不再读写。
-- 3) 删除依赖 origin 语义的索引，并重建日志日期索引（去掉 archived 条件）。
-- 未上线，直接就地归一化存量数据，无需回滚脚本。

UPDATE note SET archived = 0 WHERE archived <> 0;
UPDATE note SET visibility = 'PRIVATE' WHERE visibility <> 'PRIVATE';

DROP INDEX IF EXISTS idx_note_explore;
DROP INDEX IF EXISTS idx_note_internal;
DROP INDEX IF EXISTS idx_note_logdate;
CREATE INDEX idx_note_logdate ON note(owner_id, log_date) WHERE deleted = 0;
