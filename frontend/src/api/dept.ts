import { get, post, put, del } from './http'
import type { components } from '@/types/api'

export type Department = components['schemas']['Department']
export type SyncStatus = components['schemas']['SyncStatus']

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
