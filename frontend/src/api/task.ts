import { get, post, put, del } from './http'
import type { components } from '@/types/api'
import type { Priority } from '@/utils/constants'

export type TaskVO = components['schemas']['TaskVO']
export type TaskDetailVO = components['schemas']['TaskDetailVO']
export type CountVO = components['schemas']['CountVO']
export type Scope = components['schemas']['Scope']
export type CycleRule = components['schemas']['CycleRule']

export interface TaskListData {
  list: TaskVO[]
  total: number
  page: number
  size: number
}

/** 新建/更新任务载荷（对齐 TaskCreateReq/TaskUpdateReq，更新时须带 version）。 */
export interface TaskUpsertPayload {
  title: string
  content?: string | null
  /** 父任务 id：用于创建子任务（新建时）。 */
  parentId?: number | null
  dueAt?: string | null
  remindAt?: string | null
  priority?: Priority
  cycleRule?: CycleRule | null
  categoryId?: number | null
  assigneeIds?: number[]
  ccIds?: number[]
  tagIds?: number[]
  fileIds?: number[]
  version?: number
}

export interface TaskListQuery {
  scope: Scope
  keyword?: string
  subordinate?: boolean
  date?: string
  /** 按分类过滤（含子树，ADR-015 共享分类）。 */
  categoryId?: number
  /** 按自定义栏条目过滤（仅本人栏）。 */
  menuId?: number
  page?: number
  size?: number
}

export function listTasks(query: TaskListQuery): Promise<TaskListData> {
  return get<TaskListData>('/tasks', { ...query })
}

export function counts(): Promise<CountVO> {
  return get<CountVO>('/tasks/count')
}

export function calendar(start: string, end: string): Promise<TaskListData> {
  return get<TaskListData>('/tasks/calendar', { start, end })
}

export function subtasks(parentId: number): Promise<TaskListData> {
  return get<TaskListData>('/tasks/subtasks', { parentId })
}

export function detail(id: number): Promise<TaskDetailVO> {
  return get<TaskDetailVO>(`/tasks/${id}`)
}

export function createTask(payload: TaskUpsertPayload): Promise<number> {
  return post<number>('/tasks', payload)
}

export function updateTask(id: number, payload: TaskUpsertPayload & { version: number }): Promise<void> {
  return put<void>(`/tasks/${id}`, payload)
}

export function deleteTask(id: number): Promise<void> {
  return del<void>(`/tasks/${id}`)
}

export function completeTask(id: number, body: { version: number; dueAt?: string | null }): Promise<void> {
  return post<void>(`/tasks/${id}/complete`, body)
}

export function uncompleteTask(id: number, body: { version: number; dueAt?: string | null }): Promise<void> {
  return post<void>(`/tasks/${id}/uncomplete`, body)
}

export function collectTask(id: number): Promise<void> {
  return post<void>(`/tasks/${id}/collect`)
}

export function uncollectTask(id: number): Promise<void> {
  return del<void>(`/tasks/${id}/collect`)
}
