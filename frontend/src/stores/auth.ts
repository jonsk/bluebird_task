import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as apiLogin, logout as apiLogout, refreshToken as apiRefresh, type LoginReq, type TokenVO } from '@/api/auth'
import { me as apiMe, type UserVO } from '@/api/user'
import { clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from '@/api/token'
import type { RoleCode } from '@/utils/constants'

/**
 * 认证 store（03 §4.2/§5.1）：access 内存 + refresh 持久化；启动时凭 refresh 静默续签。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const hasAccessToken = ref(false)
  const mustChangePassword = ref(false)

  const isAuthenticated = computed(() => hasAccessToken.value && user.value !== null)
  const roleCode = computed<RoleCode>(() => (user.value?.roleCode as RoleCode) ?? 'COMMON')

  function applyToken(token: TokenVO): void {
    setAccessToken(token.accessToken ?? null)
    if (token.refreshToken) setRefreshToken(token.refreshToken)
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

  /** 应用启动：如持久化 refresh 存在则静默续签并拉取用户。 */
  async function bootstrap(): Promise<boolean> {
    const refresh = getRefreshToken()
    if (!refresh) return false
    try {
      applyToken(await apiRefresh(refresh))
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
