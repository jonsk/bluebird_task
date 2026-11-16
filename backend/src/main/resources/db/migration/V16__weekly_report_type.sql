-- AI 总结生成类型（2026-11-16）
-- 一周仍只保留一份报告；用户选择数据来源，定时任务复用保存的偏好。
-- DAILY = 每日工作记录，WEEKLY = 每周工作记录（默认）。

ALTER TABLE weekly_report_preference ADD COLUMN report_type TEXT NOT NULL DEFAULT 'WEEKLY';

-- 历史报告生成时同时使用了两类记录，留空表示「旧版：每日 + 每周」。
ALTER TABLE weekly_report ADD COLUMN report_type TEXT;
