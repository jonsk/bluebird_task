<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listLoginLogs, listOperateLogs, type LoginLogVO, type OperateLogVO } from '@/api/audit'
import { listJobLogs, listOrgSyncLogs, type JobLogVO, type OrgSyncLogVO } from '@/api/adminLog'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'
import { usePagedList } from '@/composables/usePagedList'
import BbTablePage from '@/components/bb/BbTablePage.vue'

/**
 * 日志页（ADMIN / AUDITOR）。
 *
 * 四类：操作日志、登录/退出日志（可筛认证来源与事件类型）、组织同步日志、调度任务日志。
 * 用户明确要求「系统运行日志不要，但要 Casdoor 的同步、登录、退出的日志」，
 * 因此这里只承载审计与同步两类业务日志，不接入 logback 输出。
 *
 * 四组列表共用 usePagedList（含竞态取消）与 BbTablePage（表格 + 分页器外壳），
 * 列定义仍按各自业务写在插槽里，保持自定义单元格渲染不变。
 */
const auth = useAuthStore()
const isAdmin = computed(() => auth.hasRole('ADMIN'))

const tab = ref('login')
const size = 20

const loginFilter = reactive({ provider: '', eventType: '' })

const operates = usePagedList<OperateLogVO>((p, s) => listOperateLogs(p, s), { size })
const logins = usePagedList<LoginLogVO>(
  (p, s) => listLoginLogs(p, s, { provider: loginFilter.provider, eventType: loginFilter.eventType }),
  { size },
)
const orgLogs = usePagedList<OrgSyncLogVO>((p, s) => listOrgSyncLogs(p, s), { size })
const jobLogs = usePagedList<JobLogVO>((p, s) => listJobLogs(p, s), { size })

const PROVIDER_LABEL: Record<string, string> = { LOCAL: '本地', CASDOOR: 'Casdoor' }
const EVENT_LABEL: Record<string, string> = { LOGIN: '登录', LOGOUT: '退出' }

function reload(): void {
  if (tab.value === 'operate') void operates.load()
  else if (tab.value === 'login') void logins.load()
  else if (tab.value === 'orgsync') void orgLogs.load()
  else void jobLogs.load()
}

function onTabChange(): void {
  reload()
}

function resetLoginFilter(): void {
  loginFilter.provider = ''
  loginFilter.eventType = ''
  logins.page = 1
  void logins.load()
}

function orgStatusType(status: string): 'success' | 'danger' | 'warning' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILURE') return 'danger'
  return 'warning'
}

onMounted(() => {
  void logins.load()
})
</script>

<template>
  <div class="logs">
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="登录 / 退出" name="login">
        <div class="logs__filters">
          <el-select
            v-model="loginFilter.provider"
            placeholder="全部来源"
            clearable
            size="small"
            class="logs__filter"
            data-test="login-filter-provider"
            @change="() => { logins.page = 1; void logins.load() }"
          >
            <el-option label="本地" value="LOCAL" />
            <el-option label="Casdoor" value="CASDOOR" />
          </el-select>
          <el-select
            v-model="loginFilter.eventType"
            placeholder="全部事件"
            clearable
            size="small"
            class="logs__filter"
            data-test="login-filter-event"
            @change="() => { logins.page = 1; void logins.load() }"
          >
            <el-option label="登录" value="LOGIN" />
            <el-option label="退出" value="LOGOUT" />
          </el-select>
          <el-button size="small" @click="resetLoginFilter">重置</el-button>
          <el-button size="small" @click="logins.load()">刷新</el-button>
        </div>

        <BbTablePage
          :rows="logins.rows"
          :total="logins.total"
          :page="logins.page"
          :size="logins.size"
          :loading="logins.loading"
          @update:page="(p: number) => void logins.setPage(p)"
        >
          <el-table-column prop="createdAt" label="时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="username" label="账号" width="150" />
          <el-table-column label="来源" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.provider" size="small" :type="row.provider === 'CASDOOR' ? 'warning' : 'info'" effect="plain">
                {{ PROVIDER_LABEL[row.provider] ?? row.provider }}
              </el-tag>
              <span v-else class="logs__muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="事件" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.eventType === 'LOGOUT' ? 'info' : 'success'" effect="plain">
                {{ EVENT_LABEL[row.eventType] ?? '登录' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="externalId" label="外部 ID" width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.externalId || '—' }}</template>
          </el-table-column>
          <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
        </BbTablePage>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="operate">
        <BbTablePage
          :rows="operates.rows"
          :total="operates.total"
          :page="operates.page"
          :size="operates.size"
          :loading="operates.loading"
          @update:page="(p: number) => void operates.setPage(p)"
        >
          <el-table-column prop="createdAt" label="时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="action" label="动作" width="120" />
          <el-table-column prop="method" label="方法" width="80" />
          <el-table-column prop="uri" label="路径" min-width="220" show-overflow-tooltip />
          <el-table-column prop="username" label="操作人" width="120">
            <template #default="{ row }">{{ row.username || row.userId || '—' }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="duration" label="耗时(ms)" width="100" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </BbTablePage>
      </el-tab-pane>

      <el-tab-pane label="组织同步" name="orgsync">
        <BbTablePage
          :rows="orgLogs.rows"
          :total="orgLogs.total"
          :page="orgLogs.page"
          :size="orgLogs.size"
          :loading="orgLogs.loading"
          @update:page="(p: number) => void orgLogs.setPage(p)"
        >
          <el-table-column prop="startedAt" label="开始时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column prop="finishedAt" label="结束时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt) || '—' }}</template>
          </el-table-column>
          <el-table-column prop="source" label="数据源" width="100" />
          <el-table-column prop="syncType" label="类型" width="90" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="orgStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="total" label="总数" width="80" />
          <el-table-column prop="inserted" label="新增" width="80" />
          <el-table-column prop="updated" label="更新" width="80" />
          <el-table-column prop="skipped" label="跳过" width="80" />
          <el-table-column prop="errorMessage" label="错误" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '—' }}</template>
          </el-table-column>
        </BbTablePage>
      </el-tab-pane>

      <el-tab-pane v-if="isAdmin" label="调度任务" name="job">
        <BbTablePage
          :rows="jobLogs.rows"
          :total="jobLogs.total"
          :page="jobLogs.page"
          :size="jobLogs.size"
          :loading="jobLogs.loading"
          @update:page="(p: number) => void jobLogs.setPage(p)"
        >
          <el-table-column prop="startedAt" label="开始时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column prop="job" label="任务" width="150" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(ms)" width="100" />
          <el-table-column prop="message" label="信息" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '—' }}</template>
          </el-table-column>
        </BbTablePage>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.logs {
  flex: 1;
  min-height: 0;
}
.logs__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.logs__filter {
  width: 140px;
}
.logs__muted {
  color: var(--bb-text-muted);
}
</style>
