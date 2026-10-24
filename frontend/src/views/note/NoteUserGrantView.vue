<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import NoteGrantForm from '@/components/note/NoteGrantForm.vue'
import { useNoteGrants } from '@/composables/useNoteGrants'
import { approveGrant, createGrant, rejectGrant, revokeGrant, upgradeGrant } from '@/api/note'
import { useAuthStore } from '@/stores/auth'
import { formatHeaderDate } from '@/utils/date'
import type { NoteGrantMode, NoteUserGrantVO } from '@/types/note'

/**
 * 授权管理：4 Tab（我发起的 / 我授权的 / 涉及我的 / 全部[ADMIN]）。
 * 行内动作按"我与该行的关系 + 状态"显隐：
 * - 待确认 + 我是 grantee（被授权人/日志主人）→ 确认 / 拒绝
 * - 已生效 + 只读 + 我是 grantor（授权人）→ 升级为可编辑
 * - 我是 grantor 或 ADMIN → 撤销
 */
const auth = useAuthStore()
const me = computed(() => (auth.user?.id != null ? Number(auth.user.id) : null))
const isAdmin = computed(() => auth.hasRole('ADMIN'))

const TABS = computed(() => {
  const tabs = [
    { name: 'created', label: '我发起的' },
    { name: 'granted', label: '我授权的' },
    { name: 'incoming', label: '涉及我的' },
  ]
  if (isAdmin.value) tabs.push({ name: 'all', label: '全部（管理员）' })
  return tabs
})

const tab = ref('created')
const showForm = ref(false)
const submitting = ref(false)
const mode = ref<NoteGrantMode>('OWNER')
const role = ref<'VIEWER' | 'EDITOR'>('VIEWER')
const grants = useNoteGrants()

function reload(): void {
  void grants.load(tab.value)
}

onMounted(reload)
watch(tab, () => {
  grants.setPage(1)
  reload()
})

const rows = computed<NoteUserGrantVO[]>(() => grants.list.value)

function canApprove(row: NoteUserGrantVO): boolean {
  return row.status === 'PENDING' && me.value != null && Number(row.granteeId) === me.value
}
function canUpgrade(row: NoteUserGrantVO): boolean {
  return row.status === 'APPROVED' && row.role === 'VIEWER' && me.value != null && Number(row.grantorId) === me.value
}
function canRevoke(row: NoteUserGrantVO): boolean {
  if (row.status === 'REVOKED' || row.status === 'REJECTED') return false
  return isAdmin.value || (me.value != null && Number(row.grantorId) === me.value)
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: '待确认',
  APPROVED: '已生效',
  REJECTED: '已拒绝',
  REVOKED: '已撤销',
}
const MODE_LABEL: Record<string, string> = {
  OWNER: '我授权他人',
  SUPERIOR: '上级读下属',
  ADMIN: '管理员代授',
}
const ROLE_LABEL: Record<string, string> = { VIEWER: '只读', EDITOR: '可编辑' }

async function onSubmit(payload: { mode: NoteGrantMode; grantorId?: number; granteeId?: number; role: 'VIEWER' | 'EDITOR' }): Promise<void> {
  submitting.value = true
  try {
    await createGrant(payload)
    ElMessage.success(payload.mode === 'SUPERIOR' ? '已发起，等待下属确认' : '授权已生效')
    showForm.value = false
    tab.value = 'created'
    reload()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '发起失败')
  } finally {
    submitting.value = false
  }
}

async function act(kind: 'approve' | 'reject' | 'upgrade' | 'revoke', row: NoteUserGrantVO): Promise<void> {
  const id = Number(row.id)
  try {
    if (kind === 'revoke') {
      await ElMessageBox.confirm('撤销后对方将立即失去对该用户日志的访问权限，确认撤销？', '撤销授权', {
        type: 'warning',
        confirmButtonText: '撤销',
      })
    }
    if (kind === 'approve') await approveGrant(id)
    if (kind === 'reject') await rejectGrant(id)
    if (kind === 'upgrade') await upgradeGrant(id)
    if (kind === 'revoke') await revokeGrant(id)
    ElMessage.success('操作成功')
    reload()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error((e as Error)?.message || '操作失败')
  }
}
</script>

<template>
  <div class="grant-page">
    <section class="grant-page__head">
      <div>
        <h1 class="grant-page__title">授权管理</h1>
        <span class="grant-page__sub">{{ formatHeaderDate() }}</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="showForm = !showForm">发起授权</el-button>
    </section>

    <NoteGrantForm
      v-if="showForm"
      v-model:mode="mode"
      v-model:role="role"
      :submitting="submitting"
      :admin-mode="isAdmin"
      :subordinate-enabled="auth.user?.managerCanReadSubordinate !== false"
      class="grant-page__form"
      @submit="onSubmit"
      @cancel="showForm = false"
    />

    <el-tabs v-model="tab" class="grant-page__tabs">
      <el-tab-pane v-for="t in TABS" :key="t.name" :label="t.label" :name="t.name" />
    </el-tabs>

    <el-table v-loading="grants.loading.value" :data="rows" size="small" border data-test="grant-table">
      <el-table-column label="日志主人" min-width="120">
        <template #default="{ row }">{{ row.grantorName || row.grantorId }}</template>
      </el-table-column>
      <el-table-column label="授权给" min-width="120">
        <template #default="{ row }">{{ row.granteeName || row.granteeId }}</template>
      </el-table-column>
      <el-table-column label="权限" width="90">
        <template #default="{ row }">{{ ROLE_LABEL[row.role] || row.role }}</template>
      </el-table-column>
      <el-table-column label="方式" width="120">
        <template #default="{ row }">{{ MODE_LABEL[row.mode] || row.mode }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'">
            {{ STATUS_LABEL[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发起人" width="110">
        <template #default="{ row }">{{ row.createdByName || row.createdBy }}</template>
      </el-table-column>
      <el-table-column label="发起时间" width="170">
        <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="canApprove(row)">
            <el-button size="small" type="primary" data-test="grant-approve" @click="act('approve', row)">确认</el-button>
            <el-button size="small" @click="act('reject', row)">拒绝</el-button>
          </template>
          <el-button v-if="canUpgrade(row)" size="small" @click="act('upgrade', row)">升级为可编辑</el-button>
          <el-button v-if="canRevoke(row)" size="small" type="danger" text @click="act('revoke', row)">撤销</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无授权记录" :image-size="64" />
      </template>
    </el-table>

    <div class="grant-page__pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="grants.total.value"
        :page-size="grants.size.value"
        :current-page="grants.page.value"
        @current-change="(p: number) => { grants.setPage(p); reload() }"
      />
    </div>
  </div>
</template>

<style scoped>
.grant-page {
  padding: 24px var(--bb-page-padding) 40px;
  box-sizing: border-box;
}
.grant-page__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.grant-page__title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
}
.grant-page__sub {
  margin-left: 10px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.grant-page__form {
  padding: 14px 16px;
  margin-bottom: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.grant-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
