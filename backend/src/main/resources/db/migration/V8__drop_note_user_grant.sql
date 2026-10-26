-- 2026-10-26 工作日志二轮减法：整块下线「授权日志」能力
-- 可见性只剩「自己 / 上级(开关) / ADMIN」，note_user_grant 不再被任何代码引用。
-- 历史授权记录在升级前由运维导出存档（见 docs/减法评估-授权日志.md §8）。
DROP INDEX IF EXISTS uq_nug_active;
DROP INDEX IF EXISTS idx_nug_grantor;
DROP INDEX IF EXISTS idx_nug_grantee;
DROP TABLE IF EXISTS note_user_grant;
