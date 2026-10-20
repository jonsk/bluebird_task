import { get } from './http'
import type { components } from '@/types/api'

export type OperateLogVO = components['schemas']['OperateLogVO']
export type LoginLogVO = components['schemas']['LoginLogVO']

export interface OperateLogPage {
  list: OperateLogVO[]
  total: number
  page: number
  size: number
}

export interface LoginLogPage {
  list: LoginLogVO[]
  total: number
  page: number
  size: number
}

export function listOperateLogs(page = 1, size = 20): Promise<OperateLogPage> {
  return get<OperateLogPage>('/audit/operates', { page, size })
}

/** 登录/退出日志分页；provider/eventType 为空时不作为筛选条件。 */
export function listLoginLogs(
  page = 1,
  size = 20,
  filter?: { provider?: string; eventType?: string },
): Promise<LoginLogPage> {
  return get<LoginLogPage>('/audit/logins', {
    page,
    size,
    provider: filter?.provider || undefined,
    eventType: filter?.eventType || undefined,
  })
}
