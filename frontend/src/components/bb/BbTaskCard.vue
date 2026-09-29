<script setup lang="ts">
import { computed } from 'vue'
import { Star, StarFilled, Delete, Clock } from '@element-plus/icons-vue'
import BbPriorityTag from './BbPriorityTag.vue'
import type { TaskVO } from '@/api/task'
import { formatDateTime, isNearDue, isOverdue } from '@/utils/date'

/**
 * 任务卡片（03 §5.3.2）。写入口（完成/删除）仅对 owner/assignee 显示（CC 只读，02 §4.7）。
 */
const props = withDefaults(
  defineProps<{
    task: TaskVO
    collected?: boolean
    writable?: boolean
    active?: boolean
  }>(),
  { collected: false, writable: true, active: false },
)

const emit = defineEmits<{
  (e: 'toggle', task: TaskVO): void
  (e: 'collect', task: TaskVO): void
  (e: 'open', task: TaskVO): void
  (e: 'remove', task: TaskVO): void
}>()

const completed = computed(() => Boolean(props.task.completed))
const overdue = computed(() => isOverdue(props.task.dueAt, completed.value))
const nearDue = computed(() => isNearDue(props.task.dueAt, completed.value))
const assignees = computed(() => props.task.assignees ?? [])
const tags = computed(() => props.task.tags ?? [])
</script>

<template>
  <div
    class="bb-task-card"
    :class="{ 'is-completed': completed, 'is-overdue': overdue, 'is-active': active }"
    @click="emit('open', task)"
  >
    <div class="bb-task-card__main">
      <el-checkbox
        v-if="writable"
        :model-value="completed"
        class="bb-task-card__check"
        @click.stop
        @change="emit('toggle', task)"
      />
      <div class="bb-task-card__body">
        <div class="bb-task-card__title" :class="{ 'is-done': completed }">{{ task.title }}</div>
        <div v-if="task.content" class="bb-task-card__content">{{ task.content }}</div>
        <div class="bb-task-card__meta">
          <BbPriorityTag :priority="task.priority as never" />
          <span v-if="task.dueAt" class="bb-task-card__due" :class="{ 'is-overdue': overdue, 'is-near': nearDue }">
            <el-icon><Clock /></el-icon>
            {{ formatDateTime(task.dueAt) }}
          </span>
          <el-tag v-if="task.cycleRule" size="small" effect="plain" round>周期</el-tag>
          <el-tag v-for="t in tags" :key="t.id" size="small" effect="plain" round>{{ t.name }}</el-tag>
          <span v-if="task.category" class="bb-task-card__cat">{{ task.category.name }}</span>
        </div>
      </div>
    </div>

    <div class="bb-task-card__side">
      <span v-if="task.subtaskTotal" class="bb-task-card__sub">
        {{ task.subtaskCompleted }}/{{ task.subtaskTotal }}
      </span>
      <span v-if="assignees.length" class="bb-task-card__assignee">
        {{ assignees.map((a) => a.name).join('、') }}
      </span>
      <el-button text circle size="small" @click.stop="emit('collect', task)">
        <el-icon><StarFilled v-if="collected" /><Star v-else /></el-icon>
      </el-button>
      <el-button v-if="writable" text circle size="small" @click.stop="emit('remove', task)">
        <el-icon><Delete /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.bb-task-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: var(--bb-radius);
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.bb-task-card:hover {
  border-color: #d9e2ff;
  box-shadow: 0 2px 10px rgba(64, 120, 255, 0.08);
}
.bb-task-card.is-active {
  border-color: var(--bb-color-primary);
}
.bb-task-card.is-overdue {
  border-left: 3px solid #f56c6c;
}
.bb-task-card__main {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.bb-task-card__check {
  margin-top: 2px;
}
.bb-task-card__body {
  min-width: 0;
}
.bb-task-card__title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2430;
  word-break: break-all;
}
.bb-task-card__title.is-done {
  color: var(--bb-color-muted);
  text-decoration: line-through;
}
.bb-task-card__content {
  margin-top: 2px;
  font-size: 12px;
  color: var(--bb-color-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bb-task-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--bb-color-muted);
}
.bb-task-card__due {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.bb-task-card__due.is-overdue {
  color: #f56c6c;
}
.bb-task-card__due.is-near {
  color: #e6a23c;
}
.bb-task-card__side {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--bb-color-muted);
}
</style>
