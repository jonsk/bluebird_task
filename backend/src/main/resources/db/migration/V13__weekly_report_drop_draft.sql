-- 取消「草稿」状态（2026-11-10）
-- AI 生成后直接定稿（DONE），用户后续修改走周报编辑器的自动保存。
-- 历史 DRAFT 数据统一提升为 DONE，避免前端出现未知状态。
UPDATE weekly_report SET status = 'DONE' WHERE status = 'DRAFT';
