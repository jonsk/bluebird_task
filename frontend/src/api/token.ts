/**
 * token 存储（03 §5.1）：access 内存 + refresh 持久化。
 *
 * <p>独立于 Pinia，供 http.ts 拦截器无循环依赖地读取。
 *
 * <p>已知纵深防御缺口：refresh token 存 localStorage，任意 XSS 都能读取并长期冒用。
 * access 已内存化、后端 CSP 禁止内联脚本、富文本前后端双消毒，都是为了压低 XSS 概率，
 * 但彻底修复必须把 refresh token 改成后端下发的 `HttpOnly; Secure; SameSite=Strict` Cookie
 * （同时调整 /auth/refresh 与登出接口），属于跨前后端的行为变更，暂未实施。
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
