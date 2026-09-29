<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BbButton from '@/components/bb/BbButton.vue'
import BbInput from '@/components/bb/BbInput.vue'
import { externalConfig, type ExternalConfig } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/api/http'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const provider = ref<ExternalConfig['provider']>('LOCAL')

onMounted(async () => {
  try {
    const cfg = await externalConfig()
    provider.value = cfg.provider
  } catch {
    provider.value = 'LOCAL'
  }
})

async function onSubmit(): Promise<void> {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username, password: form.password })
    if (auth.mustChangePassword) {
      ElMessage.warning('首次登录，请尽快修改密码')
    }
    const redirect = (route.query.redirect as string) || '/index'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function loginOidc(): void {
  // 整页跳转 Spring 授权发起端点（前端不自管 OAuth 状态，03 §5.1）
  window.location.assign('/oauth2/authorization/oidc')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">BlueBird 任务</h1>
      <p class="login-subtitle">账号密码登录</p>

      <form class="login-form" @submit.prevent="onSubmit">
        <BbInput v-model="form.username" placeholder="用户名" />
        <BbInput v-model="form.password" type="password" placeholder="密码" show-password />
        <BbButton type="primary" block :loading="loading" @click="onSubmit">登录</BbButton>
      </form>

      <BbButton v-if="provider === 'OIDC'" block class="mt-3" @click="loginOidc">OIDC 登录</BbButton>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #eef3ff, #f7f8fa);
}
.login-card {
  width: 360px;
  padding: 40px 32px;
  background: #fff;
  border-radius: var(--bb-radius);
  box-shadow: 0 8px 32px rgba(31, 36, 48, 0.08);
}
.login-title {
  margin: 0;
  font-size: 22px;
  color: var(--bb-color-primary);
  text-align: center;
}
.login-subtitle {
  margin: 6px 0 24px;
  color: var(--bb-color-muted);
  text-align: center;
  font-size: 13px;
}
.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.mt-3 {
  margin-top: 12px;
}
</style>
