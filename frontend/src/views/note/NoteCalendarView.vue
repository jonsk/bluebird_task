<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useMonthCalendar } from '@/composables/useMonthCalendar'
import { useAuthStore } from '@/stores/auth'
import { noteListRouteOf } from '@/utils/noteRoute'
import { formatHeaderDate } from '@/utils/date'
import type { NoteCalendarScope } from '@/types/note'

/**
 * 日志日历（全页）：按月查看日志数量，点日跳列表并保留 scope。
 * - scope 选择器**不含 visible**（无独立落地页）；subordinate 开关关闭时隐藏入口；all 仅 ADMIN。
 * - 月份/scope/date 写 URL（`/notes/calendar?scope=&year=&month=&date=`），刷新/后退可恢复。
 */
const router = useRouter()
const auth = useAuthStore()

const SCOPE_LABELS: Record<NoteCalendarScope, string> = {
  mine: '我的日志',
  explore: '探索',
  shared: '被授权日志',
  internal: '本部门日志',
  subordinate: '下属日志',
  all: '全部日志',
}

const cal = useMonthCalendar()
const scopeOptions = computed<NoteCalendarScope[]>(() => {
  const list: NoteCalendarScope[] = ['mine', 'explore', 'shared', 'internal']
  if (auth.user?.managerCanReadSubordinate) list.push('subordinate')
  if (auth.hasRole('ADMIN')) list.push('all')
  return list
})

/** 月视图网格：与任务模块（BbMiniCalendar / el-calendar）一致，**周日为首列**，前置/后置补白。 */
interface Cell {
  day: string | null
  count: number
  isToday: boolean
}
const grid = computed<Cell[]>(() => {
  const first = new Date(cal.year.value, cal.month.value - 1, 1)
  const lead = first.getDay()
  const daysInMonth = new Date(cal.year.value, cal.month.value, 0).getDate()
  const counts = new Map<string, number>()
  for (const d of cal.days.value) {
    if (d.date) counts.set(d.date, Number(d.count) || 0)
  }
  const todayStr = formatHeaderDate().slice(0, 10)
  const cells: Cell[] = []
  for (let i = 0; i < lead; i++) cells.push({ day: null, count: 0, isToday: false })
  for (let d = 1; d <= daysInMonth; d++) {
    const iso = `${cal.year.value}-${String(cal.month.value).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({ day: iso, count: counts.get(iso) ?? 0, isToday: iso === todayStr })
  }
  while (cells.length % 7 !== 0) cells.push({ day: null, count: 0, isToday: false })
  return cells
})

const weekdayLabels = ['日', '一', '二', '三', '四', '五', '六']

function openDay(cell: Cell): void {
  if (!cell.day) return
  void router.push({ path: noteListRouteOf(cal.scope.value), query: { date: cell.day } })
}
</script>

<template>
  <div class="note-calendar-page">
    <section class="nc-header">
      <div class="nc-header__left">
        <h1 class="nc-header__title">日志日历</h1>
        <span class="nc-header__sub">{{ formatHeaderDate() }}</span>
      </div>
      <div class="nc-header__ops">
        <el-select
          :model-value="cal.scope.value"
          class="nc-header__scope"
          data-test="note-calendar-scope"
          @change="(v: NoteCalendarScope) => cal.setScope(v)"
        >
          <el-option v-for="s in scopeOptions" :key="s" :label="SCOPE_LABELS[s]" :value="s" />
        </el-select>
        <el-button :icon="ArrowLeft" circle @click="cal.prevMonth" />
        <span class="nc-header__month">{{ cal.year.value }} 年 {{ cal.month.value }} 月</span>
        <el-button :icon="ArrowRight" circle @click="cal.nextMonth" />
      </div>
    </section>

    <div v-if="cal.forbidden.value" class="nc-empty" data-test="note-calendar-forbidden">
      <el-empty description="当前 scope 不可用（未开启「上级可读下属日志」或无权限）" :image-size="80" />
    </div>
    <div v-else-if="cal.error.value" class="nc-empty">
      <el-empty :description="cal.error.value.message || '日历加载失败'" :image-size="80" />
      <el-button @click="cal.reload">重试</el-button>
    </div>
    <div v-else v-loading="cal.loading.value" class="nc-grid-wrap">
      <div class="nc-grid nc-grid--head">
        <div v-for="w in weekdayLabels" :key="w" class="nc-head-cell">{{ w }}</div>
      </div>
      <div class="nc-grid">
        <div
          v-for="(cell, idx) in grid"
          :key="`${cell.day ?? 'blank'}-${idx}`"
          class="nc-cell"
          :class="{ 'is-empty': !cell.day, 'is-today': cell.isToday, 'has-count': cell.count > 0 }"
          :data-test="cell.day ? `note-day-${cell.day}` : undefined"
          @click="openDay(cell)"
        >
          <span v-if="cell.day" class="nc-cell__num">{{ Number(cell.day.slice(-2)) }}</span>
          <span v-if="cell.count" class="nc-cell__count">{{ cell.count }} 条</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.note-calendar-page {
  padding: 24px var(--bb-page-padding) 40px;
  box-sizing: border-box;
}
.nc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.nc-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
}
.nc-header__sub {
  margin-left: 10px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.nc-header__ops {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nc-header__scope {
  width: 140px;
}
.nc-header__month {
  min-width: 110px;
  text-align: center;
  font-size: 14px;
}
.nc-grid-wrap {
  min-height: 320px;
}
.nc-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}
.nc-grid--head {
  margin-bottom: 6px;
}
.nc-head-cell {
  padding: 4px 0;
  text-align: center;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.nc-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 76px;
  padding: 8px;
  box-sizing: border-box;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
}
.nc-cell.is-empty {
  border-style: dashed;
  background: transparent;
  cursor: default;
}
.nc-cell.is-today {
  border-color: var(--bg-accent);
}
.nc-cell.has-count:hover {
  box-shadow: var(--bb-shadow-flat);
}
.nc-cell__num {
  font-size: 14px;
  color: var(--bb-text-secondary);
}
.nc-cell__count {
  font-size: 12px;
  color: var(--bg-accent);
}
.nc-empty {
  padding: 40px 0;
  text-align: center;
}
</style>
