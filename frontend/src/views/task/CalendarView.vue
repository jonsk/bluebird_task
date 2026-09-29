<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { calendar as apiCalendar, type TaskVO } from '@/api/task'
import { toOffsetIso } from '@/utils/date'

/**
 * 日历视图（03 §5.3.5）：按月加载区间任务（含周期展开实例，02 §4.3）。
 */
const current = ref(new Date())
const byDay = ref<Record<string, TaskVO[]>>({})
const loading = ref(false)

const monthLabel = computed(() => `${current.value.getFullYear()} 年 ${current.value.getMonth() + 1} 月`)

async function load(): Promise<void> {
  loading.value = true
  try {
    const y = current.value.getFullYear()
    const m = current.value.getMonth()
    const start = new Date(y, m, 1)
    const end = new Date(y, m + 1, 1)
    const data = await apiCalendar(toOffsetIso(start), toOffsetIso(end))
    const grouped: Record<string, TaskVO[]> = {}
    for (const task of data.list) {
      if (!task.dueAt) continue
      const key = task.dueAt.slice(0, 10)
      ;(grouped[key] ??= []).push(task)
    }
    byDay.value = grouped
  } finally {
    loading.value = false
  }
}

watch(() => [current.value.getFullYear(), current.value.getMonth()], load)
onMounted(load)
</script>

<template>
  <div class="calendar-view" v-loading="loading">
    <header class="calendar-view__header">
      <h2>日历 · {{ monthLabel }}</h2>
    </header>
    <el-calendar v-model="current">
      <template #date-cell="{ data }">
        <div class="calendar-view__cell">
          <span class="calendar-view__day">{{ data.day.slice(-2) }}</span>
          <ul class="calendar-view__tasks">
            <li
              v-for="task in (byDay[data.day] ?? []).slice(0, 3)"
              :key="`${task.id}-${task.dueAt}`"
              class="calendar-view__task"
              :class="{ 'is-done': task.completed, 'is-urgent': task.priority === 'URGENT' }"
              :title="task.title ?? ''"
            >
              {{ task.title }}
            </li>
            <li v-if="(byDay[data.day] ?? []).length > 3" class="calendar-view__more">
              +{{ (byDay[data.day] ?? []).length - 3 }}
            </li>
          </ul>
        </div>
      </template>
    </el-calendar>
  </div>
</template>

<style scoped>
.calendar-view {
  padding: 16px 20px;
}
.calendar-view__header h2 {
  margin: 0 0 12px;
  font-size: 18px;
}
.calendar-view__cell {
  height: 100%;
  overflow: hidden;
}
.calendar-view__day {
  font-size: 12px;
  color: var(--bb-color-muted);
}
.calendar-view__tasks {
  margin: 2px 0 0;
  padding: 0;
  list-style: none;
}
.calendar-view__task {
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f2430;
}
.calendar-view__task.is-done {
  text-decoration: line-through;
  color: var(--bb-color-muted);
}
.calendar-view__task.is-urgent {
  color: #f56c6c;
}
.calendar-view__more {
  font-size: 11px;
  color: var(--bb-color-muted);
}
</style>
