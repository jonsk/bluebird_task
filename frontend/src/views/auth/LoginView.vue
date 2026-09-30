<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { externalConfig, type ExternalConfig } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/api/http'

/**
 * 登录页（旧 `.login` / `.login-box`）：全屏背景 + 400px 白卡（padding 40 40 20 / radius 6），
 * 标题 24px `#555`，下方 Tab（外部认证 / 账号密码）。默认仅本地账号密码（provider=LOCAL），
 * 此时不渲染 Tab —— 单一 Tab 无意义（ADR：默认本地登录）。
 */
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const provider = ref<ExternalConfig['provider']>('LOCAL')
const activeTab = ref('local')

const hasExternal = computed(() => provider.value !== 'LOCAL')
const externalLabel = computed(() => (provider.value === 'WECHAT' ? '企业微信' : '统一认证'))

onMounted(async () => {
  try {
    const cfg = await externalConfig()
    provider.value = cfg.provider
    if (cfg.provider !== 'LOCAL') activeTab.value = 'external'
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
    if (auth.mustChangePassword) ElMessage.warning('首次登录，请尽快修改密码')
    const redirect = (route.query.redirect as string) || '/index'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof BizError ? e.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function loginOidc(): void {
  window.location.assign('/oauth2/authorization/oidc')
}
</script>

<template>
  <div class="login">
    <div class="login-box">
      <h3 class="h3">电力公司 To Do 任务管理系统</h3>

      <el-tabs v-if="hasExternal" v-model="activeTab" class="demo-tabs">
        <el-tab-pane :label="externalLabel" name="external">
          <div class="login-external">
            <el-button v-if="provider === 'OIDC'" type="primary" @click="loginOidc">前往{{ externalLabel }}登录</el-button>
            <p v-else class="login-external__tip">请使用{{ externalLabel }}扫码登录</p>
          </div>
        </el-tab-pane>
        <el-tab-pane label="账号密码" name="local">
          <form class="login-form" @submit.prevent="onSubmit">
            <el-form label-width="0">
              <el-form-item>
                <el-input v-model="form.username" size="large" placeholder="用户名" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="onSubmit">登录</el-button>
              </el-form-item>
            </el-form>
          </form>
        </el-tab-pane>
      </el-tabs>

      <form v-else class="login-form" @submit.prevent="onSubmit">
        <el-form label-width="0">
          <el-form-item>
            <el-input v-model="form.username" size="large" placeholder="用户名" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="onSubmit">登录</el-button>
          </el-form-item>
        </el-form>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  /* 旧站为 login-background.jpg 全屏图；此处用同色系渐变兜底（不引入二进制素材） */
  background: linear-gradient(135deg, #dbe6f3 0%, #c3d4e8 45%, #aac2dd 100%);
}
.login-box {
  width: 400px;
  padding: 40px 40px 20px;
  box-sizing: border-box;
  background: var(--bg-primary);
  border-radius: 6px;
}
.h3 {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: #555;
}
.demo-tabs :deep(.el-tabs__header) {
  margin-bottom: 15px;
}
.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.login-submit {
  width: 100%;
}
.login-external {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
.login-external__tip {
  color: var(--bb-text-muted);
  font-size: 13px;
}
</style>
