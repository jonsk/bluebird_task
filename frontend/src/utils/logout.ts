import type { Router } from 'vue-router'
import { externalConfig } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 统一登出（本地 + 上游）。
 *
 * 先取认证配置（此时本地 token 仍有效），再清本地会话：
 * OIDC 模式下额外整页跳转到 Casdoor 的 `post_logout_redirect_uri` 端点，
 * 清掉上游 SSO 会话，否则下次点「登录」会被 Casdoor 静默重新登录。
 */
export async function logoutEverywhere(router: Router): Promise<void> {
  const auth = useAuthStore()
  const cfg = await externalConfig().catch(() => null)
  await auth.logout()

  const upstream = cfg?.provider === 'OIDC' ? cfg.logoutUrl : null
  if (upstream) {
    window.location.assign(upstream)
    return
  }
  await router.replace('/login')
}
