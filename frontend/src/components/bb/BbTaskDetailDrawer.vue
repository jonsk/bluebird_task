<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { detail as fetchDetail, completeTask, uncompleteTask, collectTask, uncollectTask, deleteTask, type TaskDetailVO, type TaskVO } from '@/api/task'
import BbPriorityTag from './BbPriorityTag.vue'
import BbAttachmentList from './BbAttachmentList.vue'
import { useMenuStore } from '@/stores/menu'
import { formatDateTime } from '@/utils/date'
import type { RoleCode } from '@/utils/constants'

/**
 * 任务详情抽屉（03 §5.3.4）。只读展示 + 完成/收藏/编辑/删除（写权限见 02 §4.7）。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    taskId?: number | null
    currentUserId?: number | null
    roleCode?: RoleCode
    collected?: boolean
  }>(),
  { taskId: null, currentUserId: null, roleCode: 'COMMON', collected: false },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'edit', task: TaskDetailVO): void
  (e: 'changed'): void
}>()

const detail = ref<TaskDetailVO | null>(null)
const loading = ref(false)
const menuStore = useMenuStore()

onMounted(() => {
  void menuStore.load()
})

/** 当前任务已归属的自定义栏 id。 */
const taskMenuIds = computed(() => (detail.value ? menuStore.menuIdsOf(Number(detail.value.id)) : []))

/** 移动到自定义栏（03 §5.3.2：POST /menus/{id}/items）。 */
async function onMoveTo(menuId: number): Promise<void> {
  const t = detail.value
  if (!t) return
  try {
    await menuStore.addTask(menuId, Number(t.id))
    ElMessage.success('已移动到自定义栏')
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '移动失败')
  }
}

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const writable = computed(() => {
  const t = detail.value
  if (!t) return false
  if (props.roleCode === 'ADMIN') return true
  if (props.currentUserId != null && t.owner?.id === props.currentUserId) return true
  return (t.assignees ?? []).some((a) => a.id === props.currentUserId)
})

watch(
  () => [props.modelValue, props.taskId] as const,
  async ([open, id]) => {
    if (!open || id == null) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      detail.value = await fetchDetail(Number(id))
    } catch (e) {
      ElMessage.error((e as Error).message || '加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function onToggle(): Promise<void> {
  const t = detail.value
  if (!t) return
  try {
    const body = { version: Number(t.version ?? 0), dueAt: t.dueAt ?? null }
    if (t.completed) await uncompleteTask(Number(t.id), body)
    else await completeTask(Number(t.id), body)
    detail.value = await fetchDetail(Number(t.id))
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function onCollect(): Promise<void> {
  const t = detail.value
  if (!t) return
  try {
    if (props.collected) await uncollectTask(Number(t.id))
    else await collectTask(Number(t.id))
    ElMessage.success(props.collected ? '已取消收藏' : '已收藏')
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function onDelete(): Promise<void> {
  const t = detail.value
  if (!t) return
  try {
    await ElMessageBox.confirm('删除将清空关联数据（参与人/标签/附件），不可恢复。确认删除？', '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
    })
    await deleteTask(Number(t.id))
    ElMessage.success('已删除')
    visible.value = false
    emit('changed')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败')
  }
}

async function toggleSubtask(sub: TaskVO): Promise<void> {
  try {
    const body = { version: Number(sub.version ?? 0) }
    if (sub.completed) await uncompleteTask(Number(sub.id), body)
    else await completeTask(Number(sub.id), body)
    detail.value = await fetchDetail(Number(props.taskId))
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}
</script>

<template>
  <el-drawer v-model="visible" :size="420" :with-header="false">
    <div v-loading="loading" class="bb-detail">
      <template v-if="detail">
        <header class="bb-detail__head">
          <h3 :class="{ 'is-done': detail.completed }">{{ detail.title }}</h3>
          <div class="bb-detail__head-tags">
            <BbPriorityTag :priority="detail.priority as never" />
            <el-tag v-if="detail.cycleRule" size="small" effect="plain">周期</el-tag>
            <el-tag v-if="detail.status !== 'ACTIVE'" size="small" type="info">{{ detail.status }}</el-tag>
          </div>
        </header>

        <p v-if="detail.content" class="bb-detail__content">{{ detail.content }}</p>

        <el-descriptions :column="1" size="small" border class="bb-detail__desc">
          <el-descriptions-item label="截止">{{ formatDateTime(detail.dueAt) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="归属">{{ detail.owner?.name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">
            {{ (detail.assignees ?? []).map((a) => a.name).join('、') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="@知会">
            {{ (detail.ccUsers ?? []).map((a) => a.name).join('、') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.category?.name ?? '—' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="(detail.subtasks ?? []).length" class="bb-detail__section">
          <div class="bb-detail__section-title">子任务</div>
          <div v-for="sub in detail.subtasks" :key="sub.id" class="bb-detail__subtask">
            <el-checkbox :model-value="Boolean(sub.completed)" :disabled="!writable" @change="toggleSubtask(sub)">
              <span :class="{ 'is-done': sub.completed }">{{ sub.title }}</span>
            </el-checkbox>
          </div>
        </div>

        <div class="bb-detail__section">
          <BbAttachmentList :task-id="Number(detail.id)" :files="detail.files ?? []" :disabled="!writable" @changed="emit('changed')" />
        </div>

        <div class="bb-detail__actions">
          <el-button v-if="writable" :type="detail.completed ? 'default' : 'primary'" @click="onToggle">
            {{ detail.completed ? '取消完成' : '完成' }}
          </el-button>
          <el-button @click="onCollect">{{ props.collected ? '取消收藏' : '收藏' }}</el-button>
          <el-dropdown v-if="menuStore.menus.length" trigger="click" @command="onMoveTo">
            <el-button data-test="move-menu">移动到自定义栏</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="m in menuStore.menus" :key="m.id" :command="Number(m.id)">
                  {{ taskMenuIds.includes(Number(m.id)) ? '✓ ' : '' }}{{ m.name }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="writable" @click="emit('edit', detail)">编辑</el-button>
          <el-button v-if="writable" type="danger" plain @click="onDelete">删除</el-button>
        </div>
      </template>
      <div v-else class="bb-detail__placeholder">选择任务查看详情</div>
    </div>
  </el-drawer>
</template>

<style scoped>
.bb-detail {
  padding: 4px 2px;
}
.bb-detail__head h3 {
  margin: 0 0 8px;
  font-size: 16px;
  word-break: break-all;
}
.bb-detail__head h3.is-done {
  text-decoration: line-through;
  color: var(--bb-color-muted);
}
.bb-detail__head-tags {
  display: flex;
  gap: 6px;
}
.bb-detail__content {
  margin: 10px 0;
  font-size: 13px;
  color: #3b4252;
  white-space: pre-wrap;
}
.bb-detail__desc {
  margin-top: 10px;
}
.bb-detail__section {
  margin-top: 16px;
}
.bb-detail__section-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
}
.bb-detail__subtask {
  font-size: 13px;
}
.bb-detail__subtask .is-done {
  text-decoration: line-through;
  color: var(--bb-color-muted);
}
.bb-detail__actions {
  display: flex;
  gap: 8px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.bb-detail__placeholder {
  color: var(--bb-color-muted);
  font-size: 13px;
}
</style>
