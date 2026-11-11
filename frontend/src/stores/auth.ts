import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as apiLogin, logout as apiLogout, refresh as apiRefresh, type LoginReq, type TokenVO } from '@/api/auth'
import { me as apiMe, type UserVO } from '@/api/user'
import { clearTokens, hasSessionMarker, setAccessToken } from '@/api/token'
import type { RoleCode } from '@/utils/constants'

/**
 * 认证 store（03 §4.2/§5.1）：access 内存 + refresh 由后端 HttpOnly Cookie 承载。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const hasAccessToken = ref(false)
  const mustChangePassword = ref(false)

  const isAuthenticated = computed(() => hasAccessToken.value && user.value !== null)
  const roleCode = computed<RoleCode>(() => (user.value?.roleCode as RoleCode) ?? 'COMMON')

  function applyToken(token: TokenVO): void {
    setAccessToken(token.accessToken ?? null)
    hasAccessToken.value = Boolean(token.accessToken)
    mustChangePassword.value = Boolean(token.mustChangePassword)
  }

  async function fetchMe(): Promise<void> {
    user.value = await apiMe()
  }

  async function login(payload: LoginReq): Promise<void> {
    applyToken(await apiLogin(payload))
    await fetchMe()
  }

  /**
   * 应用启动：会话标记 Cookie 存在时静默续签（refresh 由浏览器自动带上），拉取用户。
   *
   * <p>先看标记再发请求：没登录过的用户不该在每次冷启动都打一次必然失败的 /auth/refresh。
   */
  async function bootstrap(): Promise<boolean> {
    if (!hasSessionMarker()) return false
    try {
      applyToken(await apiRefresh())
      await fetchMe()
      return true
    } catch {
      clearTokens()
      return false
    }
  }

  async function logout(): Promise<void> {
    try {
      if (hasAccessToken.value) await apiLogout()
    } catch {
      // 忽略登出网络错误
    } finally {
      clearTokens()
      hasAccessToken.value = false
      user.value = null
    }
  }

  function hasRole(...roles: RoleCode[]): boolean {
    return roles.includes(roleCode.value)
  }

  return {
    user,
    hasAccessToken,
    mustChangePassword,
    isAuthenticated,
    roleCode,
    login,
    logout,
    bootstrap,
    fetchMe,
    hasRole,
  }
})
