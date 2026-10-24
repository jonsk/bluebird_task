<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { calendar as apiCalendar, type TaskVO } from '@/api/task'
import { toOffsetIso } from '@/utils/date'

/**
 * 右栏日历（旧 `.calendar-box-content` → `.el-calendar.h-[280px]`，350×280）。
 * 标注当月每天的条目数；点击某天 emit('select-date', 'YYYY-MM-DD' | null) 由主列表按日过滤。
 *
 * 通用化（note 模块改造）：
 * - `loader(year, month, scope?)` **可选**：不传 → 内置任务默认取数（忽略 scope，任务页零改动）；
 *   传入 → 用调用方取数（note 传自己的日历聚合）。
 * - `scope` 变化总是触发重新取数（无论默认/自定义 loader）。
 * - `height` 通过 CSS 变量 `--bb-calendar-height` 注入，默认 280px。
 * - 竞态：请求序号保护，旧月份响应迟到不覆盖新月份。
 * - `selected` 仅在**初始化**时决定展示月份，外部后续变化不强制切月（避免与父级筛选互相拉扯）。
 */
interface CalendarDayCount {
  date: string
  count: number
}

const props = withDefaults(
  defineProps<{
    selected?: string | null
    loader?: (year: number, month: number, scope?: string) => Promise<CalendarDayCount[]>
    scope?: string
    height?: number
  }>(),
  { selected: null, loader: undefined, scope: undefined, height: 280 },
)

const emit = defineEmits<{ (e: 'select-date', date: string | null): void }>()

const current = ref(props.selected ? new Date(`${props.selected}T00:00:00`) : new Date())
const byDay = ref<Record<string, number>>({})
const loading = ref(false)
const styleVars = computed(() => ({ '--bb-calendar-height': `${props.height}px` }))

/** 内置任务默认取数：当月任务按 dueAt 日期分组计数（忽略 scope）。 */
async function defaultLoader(year: number, month: number): Promise<CalendarDayCount[]> {
  const data = await apiCalendar(toOffsetIso(new Date(year, month, 1)), toOffsetIso(new Date(year, month + 1, 1)))
  const grouped: Record<string, number> = {}
  for (const task of data.list as TaskVO[]) {
    if (!task.dueAt) continue
    const key = task.dueAt.slice(0, 10)
    grouped[key] = (grouped[key] ?? 0) + 1
  }
  return Object.entries(grouped).map(([date, count]) => ({ date, count }))
}

let seq = 0
async function load(): Promise<void> {
  const mySeq = ++seq
  loading.value = true
  try {
    const y = current.value.getFullYear()
    // loader 约定 month 为 0-based（与 Date 一致），组件内统一转换
    const m = current.value.getMonth()
    const fn = props.loader ?? defaultLoader
    const rows = await fn(y, m, props.scope)
    if (mySeq !== seq) return
    const grouped: Record<string, number> = {}
    for (const row of rows ?? []) {
      if (!row?.date) continue
      grouped[row.date] = Number(row.count) || 0
    }
    byDay.value = grouped
  } catch {
    if (mySeq !== seq) return
    // 日历取数失败不阻断主流程：清空标注，日历仍可翻月
    byDay.value = {}
  } finally {
    if (mySeq === seq) loading.value = false
  }
}

watch(
  () => [current.value.getFullYear(), current.value.getMonth(), props.scope],
  () => {
    void load()
  },
)
watch(
  () => props.loader,
  () => {
    void load()
  },
)
onMounted(() => {
  void load()
})

function onClickDay(day: string, type?: string): void {
  // 补位日（上/下月）不可点，避免误触跨月筛选
  if (type && type !== 'current-month') return
  emit('select-date', props.selected === day ? null : day)
}
</script>

<template>
  <div v-loading="loading" class="bb-calendar" :style="styleVars">
    <el-calendar v-model="current" class="bb-calendar__inner">
      <template #date-cell="{ data }">
        <div
          class="bb-calendar__cell"
          :class="{ 'is-selected': selected === data.day, 'is-outside': data.type !== 'current-month' }"
          @click="onClickDay(data.day, data.type)"
        >
          <span class="bb-calendar__day">{{ Number(data.day.slice(-2)) }}</span>
          <span v-if="byDay[data.day]" class="bb-calendar__badge">{{ byDay[data.day] }}</span>
        </div>
      </template>
    </el-calendar>
  </div>
</template>

<style scoped>
.bb-calendar {
  height: var(--bb-calendar-height, 280px);
  /* 硬约束：日历内容不得溢出到右栏下方的详情/编辑面板（用户反馈 #4）。 */
  overflow: hidden;
}
.bb-calendar__inner {
  height: var(--bb-calendar-height, 280px);
  background: var(--bg-primary);
}
/* 关键修复：EP 默认 `.el-calendar-table .el-calendar-day { height: var(--el-calendar-cell-width) }` = 85px，
   6 周 × 85px ≈ 510px，加上表头/内边距整体 ~637px，远超 280px 容器 → 日历溢出并压住详情/编辑面板。
   把日期格压到 33px（6×33=198）+ 表头/星期行/内边距 ≈ 266px，恰好收进 280px。 */
.bb-calendar__inner :deep(.el-calendar__header) {
  padding: 6px 16px 0;
  border-bottom: none;
}
.bb-calendar__inner :deep(.el-calendar__body) {
  padding: 4px 16px 4px;
}
.bb-calendar__inner :deep(.el-calendar-table thead th) {
  padding: 2px 0;
  font-size: 11px;
}
.bb-calendar__inner :deep(.el-calendar-table .el-calendar-day) {
  height: 33px;
  padding: 0;
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
.bb-calendar__cell.is-outside {
  cursor: default;
  opacity: 0.35;
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
