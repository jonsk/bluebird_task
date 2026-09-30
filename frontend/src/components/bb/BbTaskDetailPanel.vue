<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  Calendar,
  CircleClose,
  Flag,
  Link,
  Notebook,
  Paperclip,
  RefreshRight,
  Star,
  StarFilled,
  User,
} from '@element-plus/icons-vue'
import {
  deleteTask,
  detail as fetchDetail,
  updateTask,
  collectTask,
  uncollectTask,
  completeTask,
  uncompleteTask,
  type CycleRule,
  type TaskDetailVO,
  type TaskUpsertPayload,
} from '@/api/task'
import BbAttachmentList from './BbAttachmentList.vue'
import { useMetaStore } from '@/stores/meta'
import { PRIORITY, PRIORITY_LABEL, type Priority, type RoleCode } from '@/utils/constants'
import { formatDateTime, toOffsetIso } from '@/utils/date'

/**
 * 任务详情面板（旧 `.dialog-right-box`）：内联在右栏 360px（日历下方），**不是**覆盖式抽屉。
 * 逐段对齐旧站：标题行 + 截止/提醒/重复/优先级/附件/指派/备注 + 页脚（创建时间）。
 * 写权限见 02 §4.7（CC 只读）；更新回传 version（ADR-013）。
 */
const props = withDefaults(
  defineProps<{
    taskId?: number | null
    currentUserId?: number | null
    roleCode?: RoleCode
    collected?: boolean
  }>(),
  { taskId: null, currentUserId: null, roleCode: 'COMMON', collected: false },
)

const emit = defineEmits<{ (e: 'changed'): void; (e: 'edit', task: TaskDetailVO): void }>()

const metaStore = useMetaStore()
const detail = ref<TaskDetailVO | null>(null)
const loading = ref(false)

const writable = computed(() => {
  const t = detail.value
  if (!t) return false
  if (props.roleCode === 'ADMIN') return true
  if (props.currentUserId != null && t.owner?.id === props.currentUserId) return true
  return (t.assignees ?? []).some((a) => a.id === props.currentUserId)
})

watch(
  () => props.taskId,
  async (id) => {
    if (id == null) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      detail.value = await fetchDetail(Number(id))
      await metaStore.searchUsers('')
    } catch (e) {
      ElMessage.error((e as Error)?.message || '加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

/** 用当前详情拼出完整载荷（PUT 为全量更新），再叠加本次改动。 */
function payloadOf(over: Partial<TaskUpsertPayload> = {}): (TaskUpsertPayload & { version: number }) | null {
  const t = detail.value
  if (!t) return null
  return {
    title: String(t.title ?? ''),
    content: t.content ?? null,
    priority: ((t.priority as Priority) ?? 'MEDIUM'),
    dueAt: t.dueAt ?? null,
    remindAt: t.remindAt ?? null,
    categoryId: t.category?.id ?? null,
    assigneeIds: (t.assignees ?? []).map((a) => Number(a.id)),
    ccIds: (t.ccUsers ?? []).map((a) => Number(a.id)),
    tagIds: (t.tags ?? []).map((x) => Number(x.id)),
    cycleRule: (t.cycleRule as CycleRule) ?? null,
    version: Number(t.version ?? 0),
    ...over,
  }
}

async function save(over: Partial<TaskUpsertPayload>): Promise<void> {
  const t = detail.value
  const payload = payloadOf(over)
  if (!t || !payload || !writable.value) return
  try {
    await updateTask(Number(t.id), payload)
    detail.value = await fetchDetail(Number(t.id))
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '保存失败')
  }
}

// ── 各字段本地态（保存后由 detail 回填） ──
const title = ref('')
const note = ref('')
const dueLocal = ref<string>('')
const remindLocal = ref<string>('')

watch(
  detail,
  (t) => {
    title.value = String(t?.title ?? '')
    note.value = String(t?.content ?? '')
    dueLocal.value = toLocalInput(t?.dueAt)
    remindLocal.value = toLocalInput(t?.remindAt)
  },
  { immediate: true },
)

function toLocalInput(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number): string => (n < 10 ? `0${n}` : String(n))
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

const priorityValue = computed({
  get: () => ((detail.value?.priority as Priority) ?? 'MEDIUM'),
  set: (v: Priority) => void save({ priority: v }),
})

const repeatValue = computed({
  get: () => {
    const r = detail.value?.cycleRule as CycleRule | null | undefined
    return r?.freq ?? ''
  },
  set: (v: string) => {
    if (!v) {
      void save({ cycleRule: null })
      return
    }
    const due = detail.value?.dueAt ?? toOffsetIso(new Date())
    void save({ cycleRule: { freq: v as CycleRule['freq'], interval: 1, dtstart: due, until: null, tz: 'Asia/Shanghai', count: null, byDay: null } })
  },
})

const repeatLabel = computed(() => {
  const map: Record<string, string> = { DAILY: '每天重复', WEEKLY: '每周重复', MONTHLY: '每月重复' }
  const freq = (detail.value?.cycleRule as CycleRule | null | undefined)?.freq
  return freq ? map[freq] : '重复'
})

const assigneeIds = computed({
  get: () => (detail.value?.assignees ?? []).map((a) => Number(a.id)),
  set: (v: number[]) => void save({ assigneeIds: v }),
})

const assigneeLabel = computed(() => (detail.value?.assignees ?? []).map((a) => a.name).join('、') || '指派')

async function onSaveTitle(): Promise<void> {
  const next = title.value.trim()
  if (!next || next === detail.value?.title) {
    title.value = String(detail.value?.title ?? '')
    return
  }
  await save({ title: next })
}

async function onSaveNote(): Promise<void> {
  if (note.value === (detail.value?.content ?? '')) return
  await save({ content: note.value || null })
}

async function onToggleComplete(): Promise<void> {
  const t = detail.value
  if (!t || !writable.value) return
  try {
    const body = { version: Number(t.version ?? 0), dueAt: t.dueAt ?? null }
    if (t.completed) await uncompleteTask(Number(t.id), body)
    else await completeTask(Number(t.id), body)
    detail.value = await fetchDetail(Number(t.id))
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '操作失败')
  }
}

async function onCollect(): Promise<void> {
  const t = detail.value
  if (!t) return
  try {
    if (props.collected) await uncollectTask(Number(t.id))
    else await collectTask(Number(t.id))
    elmsg(props.collected ? '已取消收藏' : '已收藏')
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '操作失败')
  }
}

function elmsg(m: string): void {
  ElMessage.success(m)
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
    detail.value = null
    emit('changed')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error)?.message || '删除失败')
  }
}

async function onToggleSubtask(sub: NonNullable<TaskDetailVO['subtasks']>[number]): Promise<void> {
  if (!writable.value) return
  try {
    const body = { version: Number(sub.version ?? 0), dueAt: sub.dueAt ?? null }
    if (sub.completed) await uncompleteTask(Number(sub.id), body)
    else await completeTask(Number(sub.id), body)
    detail.value = await fetchDetail(Number(props.taskId))
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '操作失败')
  }
}
</script>

<template>
  <div v-loading="loading" class="dialog-right-box">
    <template v-if="detail">
      <div class="drb-main">
        <!-- 标题行 -->
        <div class="drbb-item drbbi-one">
          <div class="drbbi-one__row">
            <div class="drbbi-one__check">
              <el-checkbox
                :model-value="Boolean(detail.completed)"
                :disabled="!writable"
                @change="onToggleComplete"
              />
            </div>
            <el-input
              v-model="title"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              class="drbbi-one__title"
              :readonly="!writable"
              @blur="onSaveTitle"
            />
            <div class="drbbi-one__link"><el-icon :size="24"><Link /></el-icon></div>
          </div>
          <div class="drbbi-one-child">
            <el-icon class="t-b-i-b-d-icon"><Paperclip /></el-icon>
            <input class="drbbi-one-child__input" type="text" placeholder="添加步骤" disabled />
          </div>
        </div>

        <!-- 截止 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two">
          <el-icon class="drbbi-icon"><Calendar /></el-icon>
          <el-date-picker
            v-model="dueLocal"
            type="datetime"
            class="drbb-plainpicker"
            placeholder="设置截止时间"
            :disabled="!writable"
            @change="(v: string) => save({ dueAt: v ? toOffsetIso(v) : null })"
          />
          <el-icon
            v-if="detail.dueAt && writable"
            class="drbb-clear"
            @click="save({ dueAt: null })"
          ><CircleClose /></el-icon>
        </div>

        <!-- 提醒 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two">
          <el-icon class="drbbi-icon"><Bell /></el-icon>
          <el-date-picker
            v-model="remindLocal"
            type="datetime"
            class="drbb-plainpicker"
            placeholder="设置提醒时间"
            :disabled="!writable"
            @change="(v: string) => save({ remindAt: v ? toOffsetIso(v) : null })"
          />
          <el-icon
            v-if="detail.remindAt && writable"
            class="drbb-clear"
            @click="save({ remindAt: null })"
          ><CircleClose /></el-icon>
        </div>

        <!-- 重复 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two">
          <el-icon class="drbbi-icon"><RefreshRight /></el-icon>
          <el-select
            v-model="repeatValue"
            class="drbb-plainselect"
            :placeholder="repeatLabel"
            :disabled="!writable"
            clearable
          >
            <el-option label="不重复" value="" />
            <el-option label="每天" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="每月" value="MONTHLY" />
          </el-select>
        </div>

        <!-- 优先级 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two">
          <el-icon class="drbbi-icon"><Flag /></el-icon>
          <el-select v-model="priorityValue" class="drbb-plainselect" :disabled="!writable">
            <el-option v-for="p in PRIORITY" :key="p" :label="PRIORITY_LABEL[p]" :value="p" />
          </el-select>
        </div>

        <!-- 附件 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two drbbi-four y-file">
          <BbAttachmentList
            :task-id="Number(detail.id)"
            :files="detail.files ?? []"
            :disabled="!writable"
            @changed="emit('changed')"
          />
        </div>

        <!-- 指派 -->
        <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two drbbi-four">
          <el-icon class="drbbi-icon"><User /></el-icon>
          <el-select
            v-model="assigneeIds"
            multiple
            filterable
            collapse-tags
            class="drbb-assignee"
            :placeholder="assigneeLabel"
            :disabled="!writable"
          >
            <el-option v-for="u in metaStore.users" :key="u.id" :label="u.name" :value="Number(u.id)" />
          </el-select>
        </div>

        <!-- 备注 -->
        <div class="drbb-item drbbi-three">
          <el-input
            v-model="note"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            placeholder="备注"
            :readonly="!writable"
            @blur="onSaveNote"
          />
        </div>

        <!-- 子任务 -->
        <div v-if="(detail.subtasks ?? []).length" class="drbb-item drbb-shadow1 drbbi-two">
          <div class="drbb-subtasks">
            <div v-for="sub in detail.subtasks" :key="sub.id" class="drbb-subtask">
              <el-checkbox
                :model-value="Boolean(sub.completed)"
                :disabled="!writable"
                @change="onToggleSubtask(sub)"
              />
              <span :class="{ 'is-done': sub.completed }">{{ sub.title }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="drb-footer">
        <el-icon class="drb-footer__icon" :title="collected ? '取消收藏' : '收藏'" @click="onCollect">
          <StarFilled v-if="collected" /><Star v-else />
        </el-icon>
        <span class="drb-footer__time text-[12px]">创建于{{ formatDateTime(detail.createdAt) }}</span>
        <div class="drb-footer__right">
          <el-icon v-if="writable" class="drb-footer__icon" title="删除" @click="onDelete"><Notebook /></el-icon>
        </div>
      </div>
    </template>

    <div v-else class="dialog-right-box__empty">选择任务查看详情</div>
  </div>
</template>

<style scoped>
.dialog-right-box {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  background: var(--bg-secondary);
  font-size: 14px;
}
.drb-main {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding-bottom: 10px;
}
.drbb-item {
  background: var(--bg-primary);
}
.drbb-shadow1 {
  box-shadow: var(--bb-shadow-flat);
}
.drbbi-one {
  margin-top: 10px;
  padding: 16px 16px 0;
}
.drbbi-one__row {
  display: flex;
  align-items: center;
}
.drbbi-one__check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
}
.drbbi-one__title {
  flex: 1;
}
.drbbi-one__title :deep(.el-textarea__inner) {
  font-weight: 700;
  font-size: 15px;
  box-shadow: none;
  padding: 8px;
}
.drbbi-one__link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  margin-right: 4px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.drbbi-one-child {
  display: flex;
  align-items: center;
  width: 100%;
  height: 52px;
  background: var(--bg-primary);
}
.drbbi-one-child__input {
  width: 95%;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  color: inherit;
}
.t-b-i-b-d-icon {
  margin: 0 8px 0 16px;
  font-size: 16px;
  color: var(--bb-text-secondary);
}
.drbbi-two {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-top: 10px;
  padding: 6px 12px;
  box-sizing: border-box;
}
.drbbi-four {
  align-items: flex-start;
}
.drbbi-three {
  margin-top: 10px;
  padding: 8px 12px;
}
.drbbi-icon {
  margin-right: 20px;
  font-size: 18px;
  color: var(--bb-text-secondary);
}
.drbb-clear {
  margin-left: 10px;
  cursor: pointer;
  color: var(--bb-text-muted);
}
.drbb-plainpicker,
.drbb-plainselect {
  flex: 1;
}
.drbb-plainpicker :deep(.el-input__wrapper),
.drbb-plainselect :deep(.el-select__wrapper),
.drbbi-three :deep(.el-textarea__inner) {
  box-shadow: none;
  background: transparent;
}
.drbb-assignee {
  width: 100%;
}
.drbb-subtasks {
  width: 100%;
}
.drbb-subtask {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.drbb-subtask .is-done {
  color: var(--bb-text-muted);
  text-decoration: line-through;
}
.drb-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  font-size: 16px;
}
.drb-footer__time {
  color: var(--bb-text-secondary);
}
.drb-footer__right {
  display: flex;
  align-items: center;
  width: 16px;
  height: 16px;
}
.drb-footer__icon {
  cursor: pointer;
}
.dialog-right-box__empty {
  padding: 20px;
  color: var(--bb-text-muted);
  font-size: 13px;
}
</style>
