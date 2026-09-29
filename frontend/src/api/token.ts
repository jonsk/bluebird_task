/**
 * token 存储（03 §5.1）：access 内存 + refresh 持久化。
 *
 * <p>独立于 Pinia，供 http.ts 拦截器无循环依赖地读取。
 */

const REFRESH_KEY = 'bb_refresh_token'

let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

export function setRefreshToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token)
    else localStorage.removeItem(REFRESH_KEY)
  } catch {
    // 忽略隐私模式等存储异常
  }
}

export function clearTokens(): void {
  accessToken = null
  setRefreshToken(null)
}
