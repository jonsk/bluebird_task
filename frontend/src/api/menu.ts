import { get, post, put, del } from './http'
import type { components } from '@/types/api'

export type Menu = components['schemas']['Menu']
export type MenuItem = components['schemas']['MenuItem']

export function listMenus(userId?: number): Promise<Menu[]> {
  return get<Menu[]>('/menus', userId ? { userId } : undefined)
}

export function createMenu(name: string, sort?: number): Promise<number> {
  return post<number>('/menus', { name, sort })
}

export function updateMenu(id: number, name: string, sort?: number): Promise<void> {
  return put<void>(`/menus/${id}`, { name, sort })
}

export function deleteMenu(id: number): Promise<void> {
  return del<void>(`/menus/${id}`)
}

export function addMenuItem(menuId: number, taskId: number): Promise<void> {
  return post<void>(`/menus/${menuId}/items`, { taskId })
}

export function removeMenuItem(menuId: number, itemId: number): Promise<void> {
  return del<void>(`/menus/${menuId}/items/${itemId}`)
}
