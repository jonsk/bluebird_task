import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routes } from './routes'

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** 全局守卫（03 §4.3）：未登录→登录页（带 redirect）；无权限→403。 */
export function setupGuard(): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (to.meta.requiresAuth === false) return true

    if (!auth.isAuthenticated) {
      const ok = await auth.bootstrap()
      if (!ok) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }

    const roles = to.meta.roles
    if (roles && roles.length > 0 && !auth.hasRole(...roles)) {
      return { name: 'forbidden' }
    }
    return true
  })
}
