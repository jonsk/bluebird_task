-- V3: 系统默认顶级部门标记（用户反馈 #5）
--
-- 「组织管理」需要一个系统初始化的顶级部门：可改名、不可删除。
-- 用 is_system 显式标记（而非「最后一个顶级部门」之类的隐式规则），
-- 由 DefaultDepartmentRunner 在启动时保证「有且至少一个 is_system=1 的顶级部门」，
-- 并由 DepartmentService.delete 拒绝删除。
ALTER TABLE sys_department ADD COLUMN is_system INTEGER NOT NULL DEFAULT 0;
