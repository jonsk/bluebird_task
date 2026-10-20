<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, Document, Setting, User } from '@element-plus/icons-vue'
import { listConfigGroups, type ConfigGroupVO } from '@/api/adminConfig'
import { listOrgSyncLogs, type OrgSyncLogVO } from '@/api/adminLog'
import { listLoginLogs, type LoginLogVO } from '@/api/audit'
import { syncStatus, type SyncStatus } from '@/api/dept'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'

/**
 * 后台概览：一眼看清「当前登录方式 / 组织同步状态 / 最近登录」，
 * 并给出到各功能页的快捷入口（按角色过滤）。
 */
const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const groups = ref<ConfigGroupVO[]>([])
const status = ref<SyncStatus | null>(null)
const latestSync = ref<OrgSyncLogVO | null>(null)
const recentLogins = ref<LoginLogVO[]>([])

const provider = computed(() => groups.value.find((g) => g.group === 'identity')?.items.find((i) => i.key === 'provider')?.value ?? '—')
const providerLabel = computed(() => (provider.value === 'OIDC' ? 'Casdoor 单点登录' : '本地账号密码'))
const casdoorEndpoint = computed(
  () => groups.value.find((g) => g.group === 'casdoor')?.items.find((i) => i.key === 'endpoint')?.value ?? '',
)

interface Shortcut {
  label: string
  desc: string
  path: string
  icon: unknown
  roles: Array<'ADMIN' | 'AUDITOR' | 'USER_MANAGER'>
}

const SHORTCUTS: Shortcut[] = [
  { label: '用户与机构', desc: '维护部门树与账号，支持 Excel 导入导出', path: '/admin/users', icon: User, roles: ['ADMIN', 'USER_MANAGER'] },
  { label: 'Casdoor 对接', desc: '连接参数、登录方式与组织同步策略', path: '/admin/casdoor', icon: Connection, roles: ['ADMIN'] },
  { label: '日志', desc: '登录/退出、操作、组织同步与调度任务', path: '/admin/logs', icon: Document, roles: ['ADMIN', 'AUDITOR'] },
  { label: '常规设置', desc: '登录安全、日志留存、文件存储限制', path: '/admin/settings', icon: Setting, roles: ['ADMIN'] },
]

const shortcuts = computed(() => SHORTCUTS.filter((s) => auth.hasRole(...s.roles)))

async function load(): Promise<void> {
  loading.value = true
  try {
    const [g, s, l, logins] = await Promise.all([
      auth.hasRole('ADMIN') ? listConfigGroups() : Promise.resolve([] as ConfigGroupVO[]),
      syncStatus().catch(() => null),
      listOrgSyncLogs(1, 1).catch(() => null),
      listLoginLogs(1, 6).catch(() => null),
    ])
    groups.value = g
    status.value = s
    latestSync.value = l?.list?.[0] ?? null
    recentLogins.value = logins?.list ?? []
  } catch (e) {
    ElMessage.error((e as Error).message || '概览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="overview">
    <div class="overview__cards">
      <div class="overview__card">
        <p class="overview__card-label">登录方式</p>
        <p class="overview__card-value">{{ providerLabel }}</p>
        <p class="overview__card-sub">
          {{ provider === 'OIDC' ? '普通用户仅可单点登录，admin 保留账密' : '全部用户使用账号密码登录' }}
        </p>
      </div>

      <div class="overview__card">
        <p class="overview__card-label">组织同步</p>
        <p class="overview__card-value">
          <el-tag size="small" :type="status?.mode === 'PULL' ? 'success' : 'info'" effect="plain">
            {{ status?.mode ?? '—' }}
          </el-tag>
        </p>
        <p class="overview__card-sub">{{ status?.lastResult || '尚未执行过同步' }}</p>
      </div>

      <div class="overview__card">
        <p class="overview__card-label">上次同步时间</p>
        <p class="overview__card-value overview__card-value--sm">
          {{ status?.lastSyncAt ? formatDateTime(status.lastSyncAt) : '从未同步' }}
        </p>
        <p class="overview__card-sub">
          <template v-if="latestSync">
            共 {{ latestSync.total ?? 0 }} 人 · 新增 {{ latestSync.inserted ?? 0 }} · 更新
            {{ latestSync.updated ?? 0 }}
          </template>
          <template v-else>暂无同步记录</template>
        </p>
      </div>

      <div class="overview__card">
        <p class="overview__card-label">Casdoor 地址</p>
        <p class="overview__card-value overview__card-value--sm overview__card-value--mono">
          {{ casdoorEndpoint || '未配置' }}
        </p>
        <p class="overview__card-sub">{{ auth.hasRole('ADMIN') ? '在「Casdoor 对接」中修改' : '仅管理员可查看' }}</p>
      </div>
    </div>

    <section v-if="shortcuts.length" class="overview__section">
      <h3 class="overview__section-title">快捷入口</h3>
      <div class="overview__shortcuts">
        <button
          v-for="s in shortcuts"
          :key="s.path"
          type="button"
          class="overview__shortcut"
          :data-test="`overview-${s.path}`"
          @click="router.push(s.path)"
        >
          <el-icon class="overview__shortcut-icon"><component :is="s.icon" /></el-icon>
          <div class="overview__shortcut-text">
            <p class="overview__shortcut-label">{{ s.label }}</p>
            <p class="overview__shortcut-desc">{{ s.desc }}</p>
          </div>
        </button>
      </div>
    </section>

    <section class="overview__section">
      <h3 class="overview__section-title">最近登录</h3>
      <el-table :data="recentLogins" border stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="账号" width="160" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.provider" size="small" :type="row.provider === 'CASDOOR' ? 'warning' : 'info'" effect="plain">
              {{ row.provider === 'CASDOOR' ? 'Casdoor' : '本地' }}
            </el-tag>
            <span v-else class="overview__muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="事件" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.eventType === 'LOGOUT' ? 'info' : 'success'" effect="plain">
              {{ row.eventType === 'LOGOUT' ? '退出' : '登录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" min-width="130" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.overview {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
}
.overview__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.overview__card {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.overview__card-label {
  margin: 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.overview__card-value {
  margin: 6px 0 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--bb-text-primary);
}
.overview__card-value--sm {
  font-size: 14px;
}
.overview__card-value--mono {
  font-family: var(--el-font-family-mono, monospace);
  word-break: break-all;
}
.overview__card-sub {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--bb-text-muted);
}
.overview__section-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--bb-text-secondary);
}
.overview__shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}
.overview__shortcut {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  text-align: left;
  cursor: pointer;
}
.overview__shortcut:hover {
  border-color: var(--bb-color-primary);
}
.overview__shortcut-icon {
  margin-top: 2px;
  font-size: 18px;
  color: var(--bb-color-primary);
}
.overview__shortcut-label {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--bb-text-primary);
}
.overview__shortcut-desc {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--bb-text-muted);
}
.overview__muted {
  color: var(--bb-text-muted);
}
</style>
