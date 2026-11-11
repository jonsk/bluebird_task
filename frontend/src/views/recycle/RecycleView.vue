<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  emptyRecycle,
  listRecycle,
  purgeRecycleBatch,
  purgeRecycleItem,
  restoreRecycleBatch,
  restoreRecycleItem,
  type RecycleItem,
  type RecycleRef,
  type RecycleType,
} from '@/api/recycle'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'
import BbEmpty from '@/components/bb/BbEmpty.vue'
import BbTablePage from '@/components/bb/BbTablePage.vue'

/**
 * 回收站（2026-10-30）。左栏底部垃圾桶入口。
 *
 * <p>业务口径（用户拍板）：只看自己删的；永久保留，只能手动彻底删除；彻底删除/清空仅 ADMIN。
 * 升级前删除的历史记录 `deletedAt` 为 null，显示「未知」。
 */
const auth = useAuthStore()
const isAdmin = computed(() => auth.hasRole('ADMIN'))

const type = ref<'all' | 'task' | 'note'>('all')
const keyword = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const rows = ref<RecycleItem[]>([])
const selected = ref<RecycleItem[]>([])
const acting = ref(false)

const TYPE_LABEL: Record<RecycleType, string> = { TASK: '任务', NOTE: '工作日志' }

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await listRecycle(type.value, keyword.value.trim(), page.value, size)
    rows.value = data.list
    total.value = data.total
  } catch (e) {
    rows.value = []
    total.value = 0
    ElMessage.error((e as Error)?.message || '回收站加载失败')
  } finally {
    loading.value = false
  }
}

watch(type, () => {
  page.value = 1
  selected.value = []
  void load()
})

function onSearch(): void {
  page.value = 1
  selected.value = []
  void load()
}

function onSelectionChange(items: RecycleItem[]): void {
  selected.value = items
}

function onPageChange(pageNum: number): void {
  page.value = pageNum
  void load()
}

function refOf(item: RecycleItem): RecycleRef {
  return { type: item.type, id: item.id }
}

async function onRestore(item: RecycleItem): Promise<void> {
  if (!item.restorable) {
    ElMessage.warning(item.unrestorableReason || '该记录不可恢复')
    return
  }
  acting.value = true
  try {
    await restoreRecycleItem(refOf(item))
    ElMessage.success('已恢复')
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '恢复失败')
  } finally {
    acting.value = false
  }
}

async function onPurge(item: RecycleItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `「${item.title || TYPE_LABEL[item.type]}」将被彻底删除，附件与关联数据一并清除，**不可恢复**。确认继续？`,
      '彻底删除',
      { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    await purgeRecycleItem(refOf(item))
    ElMessage.success('已彻底删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '彻底删除失败')
  } finally {
    acting.value = false
  }
}

async function onRestoreBatch(): Promise<void> {
  if (!selected.value.length) return
  acting.value = true
  try {
    const result = await restoreRecycleBatch(selected.value.map(refOf))
    if (result.skipped?.length) {
      ElMessage.warning(`已恢复 ${result.affected} 条，${result.skipped.length} 条被跳过（${result.skipped[0].reason}）`)
    } else {
      ElMessage.success(`已恢复 ${result.affected} 条`)
    }
    selected.value = []
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '批量恢复失败')
  } finally {
    acting.value = false
  }
}

async function onPurgeBatch(): Promise<void> {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(
      `选中的 ${selected.value.length} 条记录将被彻底删除，附件与关联数据一并清除，**不可恢复**。确认继续？`,
      '批量彻底删除',
      { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const result = await purgeRecycleBatch(selected.value.map(refOf))
    ElMessage.success(`已彻底删除 ${result.affected} 条`)
    selected.value = []
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '批量彻底删除失败')
  } finally {
    acting.value = false
  }
}

async function onEmpty(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '清空回收站会彻底删除其中所有记录（含附件与关联数据），**不可恢复**。确认继续？',
      '清空回收站',
      { type: 'warning', confirmButtonText: '清空', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const count = await emptyRecycle(type.value)
    ElMessage.success(`已清空 ${count} 条`)
    selected.value = []
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '清空失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="recycle-view">
    <header class="recycle-view__header">
      <div class="recycle-view__title">
        <el-icon class="recycle-view__icon"><Delete /></el-icon>
        <h2>回收站</h2>
        <span class="recycle-view__hint">删除的任务与工作日志都在这里，可恢复</span>
      </div>
      <el-button v-if="isAdmin" type="danger" plain size="small" data-test="recycle-empty" @click="onEmpty">
        清空回收站
      </el-button>
    </header>

    <div class="recycle-view__toolbar">
      <el-radio-group v-model="type" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="task">任务</el-radio-button>
        <el-radio-button label="note">工作日志</el-radio-button>
      </el-radio-group>

      <el-input
        v-model="keyword"
        class="recycle-view__search"
        size="small"
        placeholder="搜索标题 / 内容"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button size="small" @click="onSearch">搜索</el-button>

      <div class="recycle-view__batch">
        <el-button
          size="small"
          :disabled="!selected.length"
          :loading="acting"
          data-test="recycle-restore-batch"
          @click="onRestoreBatch"
        >
          批量恢复
        </el-button>
        <el-button
          v-if="isAdmin"
          size="small"
          type="danger"
          plain
          :disabled="!selected.length"
          :loading="acting"
          data-test="recycle-purge-batch"
          @click="onPurgeBatch"
        >
          批量彻底删除
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="recycle-view__body">
      <BbTablePage
        v-if="rows.length"
        data-test="recycle-table"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        row-key="id"
        @update:page="onPageChange"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column label="类型" width="98">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'TASK' ? 'primary' : 'success'" effect="plain">
              {{ TYPE_LABEL[row.type as RecycleType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题 / 内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="recycle-view__item-title">{{ row.title || '（无内容）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="删除人" width="120">
          <template #default="{ row }">{{ row.deletedByName || '—' }}</template>
        </el-table-column>
        <el-table-column label="删除时间" width="170">
          <template #default="{ row }">{{ row.deletedAt ? formatDateTime(row.deletedAt) : '未知（升级前）' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              :icon="RefreshLeft"
              :disabled="!row.restorable || acting"
              :title="row.restorable ? '恢复' : row.unrestorableReason || ''"
              @click="onRestore(row as RecycleItem)"
            >
              恢复
            </el-button>
            <el-button
              v-if="isAdmin"
              size="small"
              text
              type="danger"
              :disabled="acting"
              @click="onPurge(row as RecycleItem)"
            >
              彻底删除
            </el-button>
          </template>
        </el-table-column>
      </BbTablePage>
      <BbEmpty v-else-if="!loading" description="回收站是空的" />
    </div>
  </div>
</template>

<style scoped>
.recycle-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
  background: var(--bg-primary);
}
.recycle-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.recycle-view__title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.recycle-view__title h2 {
  margin: 0;
  font-size: 16px;
  color: var(--bb-text-secondary);
}
.recycle-view__icon {
  font-size: 18px;
  color: var(--bb-text-secondary);
}
.recycle-view__hint {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.recycle-view__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.recycle-view__search {
  width: 220px;
}
.recycle-view__batch {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.recycle-view__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.recycle-view__item-title {
  color: var(--bb-text-secondary);
}
</style>
