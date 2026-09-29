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

export function listLoginLogs(page = 1, size = 20): Promise<LoginLogPage> {
  return get<LoginLogPage>('/audit/logins', { page, size })
}
