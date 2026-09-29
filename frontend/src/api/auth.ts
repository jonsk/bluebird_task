import { get, post } from './http'
import type { components } from '@/types/api'

/** 仅包装 openapi 生成类型（03 §2.7，禁止手写响应类型）。 */
export type TokenVO = components['schemas']['TokenVO']

export interface LoginReq {
  username: string
  password: string
}

export interface ExternalConfig {
  provider: 'LOCAL' | 'OIDC' | 'WECHAT'
  corpId?: string | null
  agentId?: string | null
}

export function login(data: LoginReq): Promise<TokenVO> {
  return post<TokenVO>('/auth/login', data)
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

export function refreshToken(refreshToken: string): Promise<TokenVO> {
  return post<TokenVO>('/auth/refresh', { refreshToken })
}

export function externalConfig(): Promise<ExternalConfig> {
  return get<ExternalConfig>('/auth/external/config')
}
