import { get, post, put, del } from './http'
import type { components } from '@/types/api'

export type UserVO = components['schemas']['UserVO']
export type UserCreateReq = components['schemas']['UserCreateReq']
export type UserUpdateReq = components['schemas']['UserUpdateReq']
export type PasswordUpdateReq = components['schemas']['PasswordUpdateReq']

export interface UserPage {
  list: UserVO[]
  total: number
  page: number
  size: number
}

export function me(): Promise<UserVO> {
  return get<UserVO>('/users/me')
}

export function listUsers(
  params: {
    deptId?: number
    keyword?: string
    includeSubDept?: boolean
    /** 'subordinate' = 仅我的下属（后端按"我负责的部门及其后代"过滤） */
    scope?: 'subordinate'
    page?: number
    size?: number
  } = {},
): Promise<UserPage> {
  return get<UserPage>('/users', params)
}

export function createUser(req: UserCreateReq): Promise<number> {
  return post<number>('/users', req)
}

export function updatePassword(id: number, req: PasswordUpdateReq): Promise<void> {
  return put<void>(`/users/${id}/password`, req)
}

export function updateUser(id: number, req: UserUpdateReq): Promise<void> {
  return put<void>(`/users/${id}`, req)
}

export function disableUser(id: number): Promise<void> {
  return del<void>(`/users/${id}`)
}
