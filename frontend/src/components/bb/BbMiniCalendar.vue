<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { calendar as apiCalendar, type TaskVO } from '@/api/task'
import { toOffsetIso } from '@/utils/date'

/**
 * 右栏日历（旧 `.calendar-box-content` → `.el-calendar.h-[280px]`，350×280）。
 * 标注当月每天的任务数；点击某天 emit('select-date', 'YYYY-MM-DD' | null) 由主列表按日过滤。
 */
const props = withDefaults(defineProps<{ selected?: string | null }>(), { selected: null })

const emit = defineEmits<{ (e: 'select-date', date: string | null): void }>()

const current = ref(new Date())
const byDay = ref<Record<string, TaskVO[]>>({})
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const y = current.value.getFullYear()
    const m = current.value.getMonth()
    const data = await apiCalendar(toOffsetIso(new Date(y, m, 1)), toOffsetIso(new Date(y, m + 1, 1)))
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
watch(
  () => props.selected,
  (date) => {
    if (date) current.value = new Date(`${date}T00:00:00`)
  },
)
onMounted(load)

function onClickDay(day: string): void {
  emit('select-date', props.selected === day ? null : day)
}
</script>

<template>
  <div v-loading="loading" class="bb-calendar">
    <el-calendar v-model="current" class="bb-calendar__inner">
      <template #date-cell="{ data }">
        <div class="bb-calendar__cell" :class="{ 'is-selected': selected === data.day }" @click="onClickDay(data.day)">
          <span class="bb-calendar__day">{{ Number(data.day.slice(-2)) }}</span>
          <span v-if="(byDay[data.day] ?? []).length" class="bb-calendar__badge">
            {{ (byDay[data.day] ?? []).length }}
          </span>
        </div>
      </template>
    </el-calendar>
  </div>
</template>

<style scoped>
.bb-calendar {
  height: 280px;
}
.bb-calendar__inner {
  height: 280px;
  background: var(--bg-primary);
}
.bb-calendar__inner :deep(.el-calendar__header) {
  padding: 10px 20px 0;
}
.bb-calendar__inner :deep(.el-calendar__body) {
  padding: 12px 20px 0;
}
.bb-calendar__cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 12px;
  cursor: pointer;
}
.bb-calendar__cell.is-selected {
  background: var(--bg-active);
}
.bb-calendar__badge {
  position: absolute;
  top: 0;
  right: 2px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 7px;
  background: var(--bg-accent);
  color: #fff;
  font-size: 10px;
  line-height: 14px;
  text-align: center;
}
</style>
