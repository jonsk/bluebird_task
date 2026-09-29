import { get, post, put, del } from './http'
import type { components } from '@/types/api'

export type TagVO = components['schemas']['TagVO']

export function listTags(): Promise<TagVO[]> {
  return get<TagVO[]>('/tags')
}

export function createTag(name: string): Promise<number> {
  return post<number>('/tags', { name })
}

export function updateTag(id: number, name: string): Promise<void> {
  return put<void>(`/tags/${id}`, { name })
}

export function deleteTag(id: number): Promise<void> {
  return del<void>(`/tags/${id}`)
}
