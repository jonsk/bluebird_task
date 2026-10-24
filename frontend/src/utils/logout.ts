import type { Router } from 'vue-router'
import { externalConfig } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useNoteStore } from '@/stores/note'

/**
 * 统一登出（本地 + 上游）。
 *
 * 先取认证配置（此时本地 token 仍有效），再清本地会话：
 * OIDC 模式下额外整页跳转到 Casdoor 的 `post_logout_redirect_uri` 端点，
 * 清掉上游 SSO 会话，否则下次点「登录」会被 Casdoor 静默重新登录。
 *
 * 工作日志的 store（缓存 + 会话快照）按 userId 隔离，登出时必须 `reset(uid)`，
 * 否则同浏览器换账号登录会带出上一个账号的 activeNote/pending 卡片与列表缓存。
 */
export async function logoutEverywhere(router: Router): Promise<void> {
  const auth = useAuthStore()
  const noteStore = useNoteStore()
  const uid = auth.user?.id
  const cfg = await externalConfig().catch(() => null)
  if (uid != null) noteStore.reset(Number(uid))
  await auth.logout()

  const upstream = cfg?.provider === 'OIDC' ? cfg.logoutUrl : null
  if (upstream) {
    window.location.assign(upstream)
    return
  }
  await router.replace('/login')
}
