<script setup lang="ts">
import BbTaskCard from './BbTaskCard.vue'
import BbEmpty from './BbEmpty.vue'
import type { TaskVO } from '@/api/task'
import type { RoleCode } from '@/utils/constants'

/**
 * 任务列表（03 §5.3.2）。写权限：owner/assignee/ADMIN（CC 只读，02 §4.7）。
 */
const props = withDefaults(
  defineProps<{
    tasks: TaskVO[]
    loading?: boolean
    collectedIds?: Set<number>
    currentUserId?: number | null
    roleCode?: RoleCode
    activeId?: number | null
    emptyText?: string
  }>(),
  { loading: false, collectedIds: () => new Set<number>(), currentUserId: null, roleCode: 'COMMON', activeId: null, emptyText: '暂无任务' },
)

const emit = defineEmits<{
  (e: 'toggle', task: TaskVO): void
  (e: 'collect', task: TaskVO): void
  (e: 'open', task: TaskVO): void
  (e: 'remove', task: TaskVO): void
}>()

function writableOf(task: TaskVO): boolean {
  if (props.roleCode === 'ADMIN') return true
  if (props.currentUserId != null && task.owner?.id === props.currentUserId) return true
  return (task.assignees ?? []).some((a) => a.id === props.currentUserId)
}
</script>

<template>
  <div v-loading="loading" class="bb-task-list">
    <BbTaskCard
      v-for="task in tasks"
      :key="`${task.id}-${task.dueAt ?? ''}`"
      :task="task"
      :collected="collectedIds.has(Number(task.id))"
      :writable="writableOf(task)"
      :active="activeId === Number(task.id)"
      @toggle="emit('toggle', $event)"
      @collect="emit('collect', $event)"
      @open="emit('open', $event)"
      @remove="emit('remove', $event)"
    />
    <BbEmpty v-if="!loading && !tasks.length" :description="emptyText" />
  </div>
</template>

<style scoped>
.bb-task-list {
  min-height: 120px;
}
</style>
