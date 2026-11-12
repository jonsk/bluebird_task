-- 每周工作记录（2026-11-12）
-- 需求：工作日志同款 UI/编辑器，但粒度是「一周一条」，用户自己写，和 AI 周报（weekly_report）互不相干。
--
-- 做法：复用 note 表而不是新建一套模块——富文本、附件、回收站、可见范围、乐观锁、
-- 审计都在这张表上，复制一份就是永久双份维护。这里只加「周期」维度：
--   period_type = DAY  → 原有工作日志（默认值，历史数据全部落这一档）
--   period_type = WEEK → 每周工作记录，period_start 存该周周一（ISO 周口径，Asia/Shanghai）
--
-- log_date 保持 NOT NULL（老代码依赖），周记录写入时与 period_start 相同；
-- 周记录的 log_date 不参与任何日视图（查询侧强制 period_type='DAY'）。

ALTER TABLE note ADD COLUMN period_type TEXT NOT NULL DEFAULT 'DAY';
ALTER TABLE note ADD COLUMN period_start TEXT;

-- 历史行：日日志的 period_start 就是 log_date，方便后续统一按 period_start 排序/过滤
UPDATE note SET period_start = log_date;

-- 一周一条：同一 owner 同一周只能有一条未删除的周记录。
-- 部分索引排除 deleted=1，删除后可重新创建；恢复时要单独挡重复。
CREATE UNIQUE INDEX uq_note_week_owner_start ON note(owner_id, period_start)
    WHERE deleted = 0 AND period_type = 'WEEK';

CREATE INDEX idx_note_week_list ON note(owner_id, period_start DESC)
    WHERE deleted = 0 AND period_type = 'WEEK';
