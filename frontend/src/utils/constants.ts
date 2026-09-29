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

export const PRIORITY = ['HIGH', 'MEDIUM', 'LOW'] as const
export const PARTICIPANT_ROLE = { ASSIGNEE: 'ASSIGNEE', CC: 'CC' } as const
