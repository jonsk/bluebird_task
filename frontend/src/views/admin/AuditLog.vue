<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listLoginLogs, listOperateLogs, type LoginLogVO, type OperateLogVO } from '@/api/audit'
import { formatDateTime } from '@/utils/date'

/**
 * 审计日志（03 §5.4）。ADMIN/AUDITOR 只读（02 §6.5）。
 */
const tab = ref('operate')
const loading = ref(false)
const operates = ref<OperateLogVO[]>([])
const logins = ref<LoginLogVO[]>([])
const operateTotal = ref(0)
const loginTotal = ref(0)
const page = ref(1)
const size = 20

async function loadOperates(): Promise<void> {
  loading.value = true
  try {
    const data = await listOperateLogs(page.value, size)
    operates.value = data.list
    operateTotal.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadLogins(): Promise<void> {
  loading.value = true
  try {
    const data = await listLoginLogs(page.value, size)
    logins.value = data.list
    loginTotal.value = data.total
  } finally {
    loading.value = false
  }
}

function reload(): void {
  if (tab.value === 'operate') void loadOperates()
  else void loadLogins()
}

onMounted(reload)
</script>

<template>
  <div class="audit">
    <header class="audit__header">
      <h2>审计日志</h2>
      <el-button @click="reload">刷新</el-button>
    </header>

    <el-tabs v-model="tab" @tab-change="() => { page = 1; reload() }">
      <el-tab-pane label="操作日志" name="operate">
        <el-table v-loading="loading" :data="operates" border stripe size="small">
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="action" label="动作" width="120" />
          <el-table-column prop="method" label="方法" width="80" />
          <el-table-column prop="uri" label="路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="userId" label="操作人" width="100" />
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column prop="duration" label="耗时(ms)" width="100" />
          <el-table-column prop="status" label="结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="audit__pager">
          <el-pagination
            layout="total, prev, pager, next"
            :total="operateTotal"
            :page-size="size"
            :current-page="page"
            @current-change="(p: number) => { page = p; loadOperates() }"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="登录日志" name="login">
        <el-table v-loading="loading" :data="logins" border stripe size="small">
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="username" label="账号" width="160" />
          <el-table-column prop="success" label="结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="160" />
          <el-table-column prop="userAgent" label="User-Agent" min-width="240" show-overflow-tooltip />
        </el-table>
        <div class="audit__pager">
          <el-pagination
            layout="total, prev, pager, next"
            :total="loginTotal"
            :page-size="size"
            :current-page="page"
            @current-change="(p: number) => { page = p; loadLogins() }"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.audit {
  padding: 16px 20px;
}
.audit__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.audit__header h2 {
  margin: 0;
  font-size: 18px;
}
.audit__pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
