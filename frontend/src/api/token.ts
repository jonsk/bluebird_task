/**
 * token 存储（03 §5.1）：access 只在内存，refresh 完全由后端 HttpOnly Cookie 承载。
 *
 * <p>独立于 Pinia，供 http.ts 拦截器无循环依赖地读取。
 *
 * <p>refresh token 不再落 localStorage：XSS 拿不到它，长期凭证只在 Cookie 里，
 * 且 Cookie 是 `HttpOnly; SameSite=Strict; Path=/api/v1/auth`。
 * 因此这里只剩「内存里的 access」与「是否可能已登录」的粗判。
 */

/** 后端写的非 HttpOnly 会话标记（值为 1），只用来避免冷启动必然失败的刷新请求。 */
const SESSION_MARKER = 'bb_session'

let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/**
 * 是否存在会话标记 Cookie。
 *
 * <p>它不是凭证，可以伪造，只用于省掉一次无意义的 `/auth/refresh`：
 * 判断错（有标记但 Cookie 已失效）也只是多一次失败的刷新，随后跳登录页。
 */
export function hasSessionMarker(): boolean {
  if (typeof document === 'undefined' || !document.cookie) return false
  return document.cookie
    .split(';')
    .some((part) => part.trim() === `${SESSION_MARKER}=1`)
}

/** 清空内存 access；服务端 Cookie 由 /auth/logout 或过期清理。 */
export function clearTokens(): void {
  accessToken = null
}
