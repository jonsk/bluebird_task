<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Refresh } from '@element-plus/icons-vue'
import { listConfigGroups, testCasdoor, type ConfigGroupVO, type ConfigTestResult } from '@/api/adminConfig'
import { latestOrgSync, type OrgSyncLogVO } from '@/api/adminLog'
import { syncOrg, syncStatus, type SyncStatus } from '@/api/dept'
import ConfigGroupForm from '@/components/admin/ConfigGroupForm.vue'
import { formatDateTime } from '@/utils/date'

/**
 * Casdoor 对接页（ADMIN）。
 *
 * 三段式：① 连接参数（casdoor）② 登录方式（identity）③ 组织同步（orgsync）。
 * 表单完全由 `ConfigDefinition` 驱动，后端新增配置项时此页无需改动。
 */
const GROUP_ORDER = ['casdoor', 'identity', 'orgsync'] as const

const loading = ref(false)
const groups = ref<ConfigGroupVO[]>([])
const testing = ref(false)
const testResult = ref<ConfigTestResult | null>(null)
const status = ref<SyncStatus | null>(null)
const latest = ref<OrgSyncLogVO | null>(null)
const syncing = ref(false)

const ordered = computed(() =>
  GROUP_ORDER.map((g) => groups.value.find((x) => x.group === g)).filter((g): g is ConfigGroupVO => Boolean(g)),
)

async function load(): Promise<void> {
  loading.value = true
  try {
    groups.value = await listConfigGroups()
  } catch (e) {
    ElMessage.error((e as Error).message || '配置加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRuntime(): Promise<void> {
  try {
    status.value = await syncStatus()
  } catch {
    status.value = null
  }
  try {
    latest.value = await latestOrgSync()
  } catch {
    latest.value = null
  }
}

onMounted(async () => {
  await Promise.all([load(), loadRuntime()])
})

function onSaved(updated: ConfigGroupVO): void {
  const i = groups.value.findIndex((g) => g.group === updated.group)
  if (i >= 0) groups.value.splice(i, 1, updated)
  void loadRuntime()
}

async function onTest(): Promise<void> {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testCasdoor()
    if (testResult.value.ok) ElMessage.success('连接正常')
    else ElMessage.warning(testResult.value.message || '连接失败')
  } catch (e) {
    ElMessage.error((e as Error).message || '测试失败')
  } finally {
    testing.value = false
  }
}

async function onSync(): Promise<void> {
  syncing.value = true
  try {
    const msg = await syncOrg()
    ElMessage.success(msg || '同步已触发')
    await loadRuntime()
  } catch (e) {
    ElMessage.error((e as Error).message || '同步失败')
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="casdoor">
    <section class="casdoor__runtime">
      <div class="casdoor__runtime-item">
        <span class="casdoor__runtime-label">同步模式</span>
        <el-tag size="small" :type="status?.mode === 'PULL' ? 'success' : 'info'" effect="plain">
          {{ status?.mode ?? '—' }}
        </el-tag>
      </div>
      <div class="casdoor__runtime-item">
        <span class="casdoor__runtime-label">上次同步</span>
        <span class="casdoor__runtime-value">{{ status?.lastSyncAt ? formatDateTime(status.lastSyncAt) : '从未同步' }}</span>
      </div>
      <div class="casdoor__runtime-item">
        <span class="casdoor__runtime-label">最近结果</span>
        <span class="casdoor__runtime-value">{{ status?.lastResult || '—' }}</span>
      </div>
      <div class="casdoor__runtime-actions">
        <el-button size="small" :loading="testing" data-test="casdoor-test" @click="onTest">
          <el-icon><Connection /></el-icon>
          <span class="ml-4">测试连通性</span>
        </el-button>
        <el-button size="small" type="primary" :loading="syncing" data-test="casdoor-sync" @click="onSync">
          <el-icon><Refresh /></el-icon>
          <span class="ml-4">立即同步</span>
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="testResult"
      :type="testResult.ok ? 'success' : 'error'"
      :closable="false"
      show-icon
      class="casdoor__alert"
    >
      <template #title>
        {{ testResult.ok ? `连接成功（${testResult.latencyMs} ms）` : testResult.message }}
      </template>
      <div v-if="testResult.ok" class="casdoor__alert-body">
        <p v-if="testResult.issuer">issuer：{{ testResult.issuer }}</p>
        <p v-if="testResult.authorizationEndpoint">authorize：{{ testResult.authorizationEndpoint }}</p>
        <p v-if="testResult.tokenEndpoint">token：{{ testResult.tokenEndpoint }}</p>
      </div>
    </el-alert>

    <el-alert v-if="latest" type="info" :closable="false" show-icon class="casdoor__alert">
      <template #title>
        最近一次同步：{{ latest.status }} · 共 {{ latest.total ?? 0 }} 人，新增 {{ latest.inserted ?? 0 }}、更新
        {{ latest.updated ?? 0 }}、跳过 {{ latest.skipped ?? 0 }}
      </template>
      <div v-if="latest.errorMessage" class="casdoor__alert-body">{{ latest.errorMessage }}</div>
    </el-alert>

    <ConfigGroupForm v-for="g in ordered" :key="g.group" :group="g" @saved="onSaved" />

    <p class="casdoor__tip">
      提示：切换「认证方式」为 OIDC 后，普通用户将无法使用账号密码登录（仅内置 admin 保留账密入口，用于应急）。
      回调地址需与 Casdoor Application 中登记的完全一致。
    </p>
  </div>
</template>

<style scoped>
.casdoor {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}
.casdoor__runtime {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 24px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.casdoor__runtime-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.casdoor__runtime-label {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.casdoor__runtime-value {
  font-size: 13px;
  color: var(--bb-text-secondary);
}
.casdoor__runtime-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.ml-4 {
  margin-left: 4px;
}
.casdoor__alert-body {
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}
.casdoor__alert-body p {
  margin: 0;
}
.casdoor__tip {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--bb-text-muted);
}
</style>
