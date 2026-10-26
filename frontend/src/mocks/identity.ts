import { dataOf } from './fixtures'

/**
 * Mock 身份（多账号登录）— 仅用于 e2e 的**权限门控**场景，不做数据归属隔离。
 *
 * 为什么需要：真实后端里 `/notes?scope=all` 仅 ADMIN 可读、`scope=subordinate` 需要
 * `MANAGER_CAN_READ_SUBORDINATE`，而 MSW 原来只认 admin 一个账号，权限分支无法在
 * 浏览器 e2e 里覆盖（此前靠后端矩阵脚本兜底）。
 *
 * 约定：
 * - 登录成功返回 `accessToken = mock.access.<username>` / `refreshToken = mock.refresh.<username>`，
 *   刷新与 `users/me` 都从 token 反解身份，刷新页面后身份不丢（token 存 localStorage）。
 * - 未带 token（或 token 非法）时回落到 admin，保证历史用例与视觉基线不变。
 * - 数据（日志/任务/标签）仍以 fixture 的 admin 为 owner 视角生成，切账号只影响
 *   权限判定与左栏入口，不影响列表内容；断言也应只针对权限行为。
 */

export type MockRoleCode = 'ADMIN' | 'COMMON' | 'AUDITOR'

export interface MockAccount {
  username: string
  password: string
  name: string
  id: number
  roleCode: MockRoleCode
  deptId: number | null
  deptName: string | null
  managerCanReadSubordinate: boolean
}

const ME = dataOf<{ id?: number; name?: string; deptId?: number; deptName?: string }>('users/GET.me')

/** 可选登录账号：admin 与视觉基线保持一致；其余用于权限用例 */
export const MOCK_ACCOUNTS: MockAccount[] = [
  {
    username: 'admin',
    password: 'admin123',
    name: ME.name ?? '管理员',
    id: Number(ME.id ?? 1),
    roleCode: 'ADMIN',
    deptId: (ME.deptId as number) ?? 10,
    deptName: (ME.deptName as string) ?? '研发部',
    managerCanReadSubordinate: true,
  },
  {
    username: 'common',
    password: 'Test@1234',
    name: '普通员工',
    id: 2101,
    roleCode: 'COMMON',
    deptId: 10,
    deptName: '研发部',
    managerCanReadSubordinate: false,
  },
  {
    username: 'nodept',
    password: 'Test@1234',
    name: '无部门员工',
    id: 2102,
    roleCode: 'COMMON',
    deptId: null,
    deptName: null,
    managerCanReadSubordinate: false,
  },
  {
    username: 'auditor',
    password: 'Test@1234',
    name: '审计员',
    id: 2103,
    roleCode: 'AUDITOR',
    deptId: 10,
    deptName: '研发部',
    managerCanReadSubordinate: false,
  },
]

export const DEFAULT_ACCOUNT = MOCK_ACCOUNTS[0]

export const ACCESS_PREFIX = 'mock.access.'
export const REFRESH_PREFIX = 'mock.refresh.'

export function accessTokenOf(username: string): string {
  return `${ACCESS_PREFIX}${username}`
}

export function refreshTokenOf(username: string): string {
  return `${REFRESH_PREFIX}${username}`
}

function accountByName(username?: string | null): MockAccount | undefined {
  if (!username) return undefined
  return MOCK_ACCOUNTS.find((a) => a.username === username)
}

/** 从 refresh token 反解账号（bootstrap 静默续签用） */
export function accountFromRefreshToken(token?: string | null): MockAccount | undefined {
  if (!token || !token.startsWith(REFRESH_PREFIX)) return undefined
  return accountByName(token.slice(REFRESH_PREFIX.length))
}

/** 从请求头反解账号；无 token / 非法 token 回落 admin */
export function accountFromRequest(request: Request): MockAccount {
  const auth = request.headers.get('Authorization') ?? ''
  const token = auth.replace(/^Bearer\s+/i, '')
  if (token.startsWith(ACCESS_PREFIX)) {
    const found = accountByName(token.slice(ACCESS_PREFIX.length))
    if (found) return found
  }
  return DEFAULT_ACCOUNT
}

/** `/users/me` 响应用的用户对象（保留 fixture 里的 mobile 等字段） */
export function meOf(account: MockAccount): Record<string, unknown> {
  return {
    id: account.id,
    username: account.username,
    name: account.name,
    mobile: '138****0100',
    deptId: account.deptId,
    deptName: account.deptName,
    roleCode: account.roleCode,
    mustChangePassword: false,
    managerCanReadSubordinate: account.managerCanReadSubordinate,
  }
}

/** 便捷判定：请求者是否 ADMIN / 是否有查看下属权限 */
export function isAdminRequest(request: Request): boolean {
  return accountFromRequest(request).roleCode === 'ADMIN'
}

export function canReadSubordinate(request: Request): boolean {
  return accountFromRequest(request).managerCanReadSubordinate
}
