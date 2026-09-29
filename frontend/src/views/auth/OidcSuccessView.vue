<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { setAccessToken, setRefreshToken } from '@/api/token'

/**
 * OIDC 整页回调落点（03 §5.1）：token 经 hash fragment 传入（防落历史/Referer/日志），
 * 读取后立即清除 fragment 再拉 /users/me。
 */
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const access = params.get('access_token')
  const refresh = params.get('refresh_token')
  const error = params.get('error')

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
  if (refresh) setRefreshToken(refresh)
  try {
    await auth.fetchMe()
    await router.replace('/index')
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
  color: var(--bb-color-muted);
}
</style>
