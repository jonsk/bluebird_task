<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { setAccessToken } from '@/api/token'
import { safeRedirect } from '@/utils/format'

/**
 * OIDC 整页回调落点（03 §5.1）：access token 经 hash fragment 传入（防落历史/Referer/日志），
 * 读取后立即清除 fragment 再拉 /users/me。refresh token 已改由后端 HttpOnly Cookie 下发，不在 URL 中。
 */
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const access = params.get('access_token')
  const error = params.get('error')
  // 后端在 state 中携带了用户原本要去的页面，登录后原路返回；只接受站内单个 / 开头的路径。
  const redirect = safeRedirect(params.get('redirect'))

  history.replaceState(null, '', window.location.pathname)

  if (error) {
    ElMessage.error('登录失败：' + error)
    await router.replace('/login')
    return
  }
  if (!access) {
    ElMessage.error('登录信息缺失')
    await router.replace('/login')
    return
  }
  setAccessToken(access)
  try {
    await auth.fetchMe()
    await router.replace(redirect)
  } catch {
    await router.replace('/login')
  }
})
</script>

<template>
  <div class="oidc-success">登录处理中…</div>
</template>

<style scoped>
.oidc-success {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--bb-text-muted);
}
</style>
