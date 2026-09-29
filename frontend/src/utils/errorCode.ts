/**
 * 错误码映射（03 §4.1）。与后端 ErrorCode（02 §1.4）逐一对齐。
 */
export const ERROR_CODE = {
  OK: 0,
  SYSTEM_ERROR: 10000,
  PARAM_ERROR: 10001,
  UNAUTHENTICATED: 10002,
  FORBIDDEN: 10003,
  NOT_FOUND: 10004,
  REPEAT_SUBMIT: 10005,
  VERSION_CONFLICT: 10006,
  TOO_MANY_REQUESTS: 10007,
  USER_NOT_FOUND: 20001,
  USER_DISABLED: 20002,
  BAD_CREDENTIALS: 20003,
  NO_PERMISSION_EXTERNAL: 20004,
  TOKEN_INVALID: 20005,
  ACCOUNT_LOCKED: 20006,
  TASK_NOT_FOUND: 30001,
  TASK_DELETED: 30002,
  TASK_ALREADY_COMPLETED: 30003,
  PARENT_NOT_FOUND: 30004,
  FILE_NOT_FOUND: 40001,
  FILE_TOO_LARGE: 40002,
  FILE_TYPE_NOT_ALLOW: 40003,
  SYNC_IN_PROGRESS: 60001,
} as const

export type ErrorCodeValue = (typeof ERROR_CODE)[keyof typeof ERROR_CODE]

const MESSAGES: Record<number, string> = {
  10000: '系统异常，请稍后重试',
  10001: '参数校验失败',
  10002: '未登录或登录已过期',
  10003: '无权限执行该操作',
  10004: '资源不存在',
  10005: '请勿重复提交',
  10006: '内容已被他人修改，请刷新后重试',
  10007: '请求过于频繁，请稍后再试',
  20001: '用户不存在',
  20002: '用户已被禁用',
  20003: '用户名或密码错误',
  20004: '外部身份源无权限',
  20005: '登录凭证无效或已过期',
  20006: '账号已锁定，请稍后再试',
  30001: '任务不存在',
  30002: '任务已删除',
  30003: '任务已完成',
  30004: '父任务不存在',
  40001: '文件不存在',
  40002: '文件超出大小限制',
  40003: '不支持的文件类型',
  60001: '同步进行中，请稍后再试',
}

export function messageOf(code: number, fallback = '请求失败'): string {
  return MESSAGES[code] ?? fallback
}
