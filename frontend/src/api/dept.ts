import { get, post, put, del, http, BizError } from './http'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'
import { downloadFile } from './download'
import type { components } from '@/types/api'

export type Department = components['schemas']['Department']
export type SyncStatus = components['schemas']['SyncStatus']
export type DeptImportResult = components['schemas']['DeptImportResult']

export interface DeptPayload {
  name?: string
  parentId?: number | null
  leaderId?: number | null
}

export function listDepartments(): Promise<Department[]> {
  return get<Department[]>('/departments')
}

export function createDepartment(payload: DeptPayload & { name: string }): Promise<number> {
  return post<number>('/departments', payload)
}

export function updateDepartment(id: number, payload: DeptPayload): Promise<void> {
  return put<void>(`/departments/${id}`, payload)
}

export function deleteDepartment(id: number): Promise<void> {
  return del<void>(`/departments/${id}`)
}

export function syncOrg(): Promise<string> {
  return post<string>('/org/sync')
}

export function syncStatus(): Promise<SyncStatus> {
  return get<SyncStatus>('/org/sync/status')
}

/** 下载部门导入模板（.xlsx）。 */
export function downloadDeptTemplate(): Promise<string> {
  return downloadFile('/departments/import-template', '部门导入模板.xlsx')
}

/** 导出全部部门（.xlsx）。 */
export function exportDepartments(): Promise<string> {
  return downloadFile('/departments/export', '部门数据.xlsx')
}

/**
 * 导入部门（.xlsx）。后端**先全量校验**：`ok=false` 表示整份未导入，
 * `errors` 给出每行原因（`row` 为 Excel 1-based 行号）。
 */
export async function importDepartments(file: File): Promise<DeptImportResult> {
  const form = new FormData()
  form.append('file', file)
  const resp = await http.post<{ code: number; message: string; data: DeptImportResult; traceId?: string }>(
    '/departments/import',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  if (resp.data.code !== ERROR_CODE.OK) {
    throw new BizError(resp.data.code, resp.data.message || messageOf(resp.data.code), resp.data.traceId)
  }
  return resp.data.data
}
