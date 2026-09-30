/** 任务视图 scope（03 §4.3 路由↔scope 映射，与后端 GET /tasks?scope= 对齐）。 */
export const TASK_SCOPE = {
  day: 'day',
  week: 'week',
  assigned: 'assigned',
  joined: 'joined',
  collect: 'collect',
  all: 'all',
} as const

export type TaskScope = (typeof TASK_SCOPE)[keyof typeof TASK_SCOPE]

/** 路由名 → scope（R9-契约：非直映射）。 */
export const ROUTE_SCOPE_MAP: Record<string, TaskScope> = {
  index: TASK_SCOPE.day,
  myWeek: TASK_SCOPE.week,
  myJoin: TASK_SCOPE.joined,
  myDo: TASK_SCOPE.assigned,
  myCollect: TASK_SCOPE.collect,
  allTask: TASK_SCOPE.all,
}

/** 角色四值（02 §2.4，无角色表）。 */
export const ROLE_CODE = {
  ADMIN: 'ADMIN',
  AUDITOR: 'AUDITOR',
  USER_MANAGER: 'USER_MANAGER',
  COMMON: 'COMMON',
} as const

export type RoleCode = (typeof ROLE_CODE)[keyof typeof ROLE_CODE]

/**
 * 角色说明（ADR-003：**无角色表**，角色是固定的四值枚举，用于划分功能权限，不可增删改）。
 * 权限落点：ADMIN/USER_MANAGER → 用户与部门管理（`@PreAuthorize`）；ADMIN/AUDITOR → 审计日志；
 * 所有已登录用户都可创建/管理自己的任务（任务可写权限由 owner/assignee/ADMIN 判定，与角色无关）。
 */
export const ROLE_DESC: Record<RoleCode, string> = {
  ADMIN: '超级管理员 · 全部权限（用户/部门/审计/任务）',
  USER_MANAGER: '用户管理员 · 管理用户与部门（不含审计日志）',
  AUDITOR: '审计员 · 只读查看操作日志与登录日志',
  COMMON: '普通用户 · 创建与管理自己的任务',
}

export const PRIORITY = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const
export type Priority = (typeof PRIORITY)[number]

export const PRIORITY_LABEL: Record<Priority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

export const PARTICIPANT_ROLE = { ASSIGNEE: 'ASSIGNEE', CC: 'CC' } as const
