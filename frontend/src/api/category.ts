import { get, post, put, del } from './http'
import type { components } from '@/types/api'

export type CategoryNode = components['schemas']['Category']
export type CategoryScope = 'PERSONAL' | 'DEPARTMENT' | 'ORG'

export interface CategoryPayload {
  name: string
  parentId?: number | null
  scope?: CategoryScope
  deptId?: number | null
}

export function listCategories(scope?: CategoryScope): Promise<CategoryNode[]> {
  return get<CategoryNode[]>('/categories', scope ? { scope } : undefined)
}

export function createCategory(payload: CategoryPayload): Promise<number> {
  return post<number>('/categories', payload)
}

export function updateCategory(id: number, payload: CategoryPayload): Promise<void> {
  return put<void>(`/categories/${id}`, payload)
}

export function deleteCategory(id: number): Promise<void> {
  return del<void>(`/categories/${id}`)
}
