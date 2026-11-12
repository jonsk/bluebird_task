<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { stripMarkdown } from '@/utils/markdown'
import type { NoteBrief, NotePeriod } from '@/types/note'
import { weekRangeLabel } from '@/utils/date'

/** NoteMiniList — 右栏小列表（无分页），底部「查看全部」跳转。 */
const props = withDefaults(
  defineProps<{
    notes: NoteBrief[]
    loading?: boolean
    activeId?: number | null
    /** DAY 显示日期；WEEK 显示 `2026-11-02 ~ 2026-11-08` */
    period?: NotePeriod
  }>(),
  { loading: false, activeId: null, period: 'DAY' },
)

/** 周记录的 logDate 就是该周周一，展示时展开成区间。 */
function noteLabel(note: NoteBrief): string {
  const date = note.logDate ?? ''
  return props.period === 'WEEK' && date ? weekRangeLabel(date) : date
}

const emit = defineEmits<{
  (e: 'open', id: number): void
  (e: 'view-all'): void
}>()
</script>

<template>
  <div class="note-mini-list">
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-empty
      v-else-if="!notes.length"
      :description="period === 'WEEK' ? '本周暂无记录' : '今日暂无日志'"
      :image-size="48"
    />
    <ul v-else class="note-mini-list__items">
      <li
        v-for="note in notes"
        :key="note.id"
        class="note-mini-list__item"
        :class="{ 'is-active': activeId != null && note.id === activeId }"
        @click="note.id != null && emit('open', note.id)"
      >
        <span class="note-mini-list__date">{{ noteLabel(note) }}</span>
        <span class="note-mini-list__summary">{{ stripMarkdown(note.contentSummary) || (period === 'WEEK' ? '本周暂无内容' : '（无内容）') }}</span>
      </li>
    </ul>
    <div class="note-mini-list__footer">
      <el-button text size="small" :icon="ArrowRight" @click="emit('view-all')">查看全部</el-button>
    </div>
  </div>
</template>

<style scoped>
.note-mini-list__items {
  margin: 0;
  padding: 0;
  list-style: none;
}
.note-mini-list__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  font-size: 13px;
}
.note-mini-list__item:hover {
  background: var(--bg-hover);
}
.note-mini-list__item.is-active {
  background: var(--bg-active);
}
.note-mini-list__date {
  flex: 0 0 auto;
  color: var(--bb-text-muted);
}
.note-mini-list__summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--bb-text-secondary);
}
.note-mini-list__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
</style>
