import { get } from './http'
import type { components } from '@/types/api'

export type OrgSyncLogVO = components['schemas']['OrgSyncLogVO']
export type JobLogVO = components['schemas']['JobLogVO']

export interface OrgSyncLogPage {
  list: OrgSyncLogVO[]
  total: number
  page: number
  size: number
}

export interface JobLogPage {
  list: JobLogVO[]
  total: number
  page: number
  size: number
}

/** 组织同步日志分页（ADMIN / AUDITOR）。 */
export function listOrgSyncLogs(page = 1, size = 20): Promise<OrgSyncLogPage> {
  return get<OrgSyncLogPage>('/org/sync/logs', { page, size })
}

/**
 * 最近一次组织同步记录。
 *
 * <p>服务端在无记录时会省略 `data`（Jackson `non_null`），响应体只剩 `{code,message,traceId}`；
 * 因此这里可能拿到 `undefined`，统一收敛成 `null` 交给调用方，避免类型与运行时不一致。
 */
export async function latestOrgSync(): Promise<OrgSyncLogVO | null> {
  const data = await get<OrgSyncLogVO | null | undefined>('/org/sync/latest')
  return data ?? null
}

/** 调度任务日志分页（ADMIN）。 */
export function listJobLogs(page = 1, size = 20): Promise<JobLogPage> {
  return get<JobLogPage>('/jobs/logs', { page, size })
}
