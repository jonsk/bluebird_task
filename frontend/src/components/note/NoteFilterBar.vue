<script setup lang="ts">
import type { NoteQuery, NoteQueryPatch, NoteSort } from '@/types/note'

/**
 * NoteFilterBar — 列表正文区的补充筛选（排序 / 日期）。
 *
 * 关键词搜索已提到标题栏（`NoteSearchInput`），与「我的一天」保持同一位置；
 * 这里只保留排序与日期。「每日工作记录」(scope=mine) 只有关键词，外壳不再渲染本组件。
 * 所有变更 emit('change', patch) 由外壳（useNoteList）统一写入；本组件不请求。
 */
withDefaults(
  defineProps<{
    query: NoteQuery
    loading?: boolean
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  (e: 'change', patch: NoteQueryPatch): void
}>()

const SORT_LABELS: Record<NoteSort, string> = {
  log_date_desc: '日志日期 ↓',
  log_date_asc: '日志日期 ↑',
  created_desc: '创建时间 ↓',
  updated_desc: '更新时间 ↓',
}
</script>

<template>
  <div class="note-filter-bar" data-test="note-filter-bar">
    <div class="note-filter-bar__row">
      <el-select
        :model-value="query.sort ?? 'log_date_desc'"
        class="note-filter-bar__sel"
        @change="(v: NoteSort) => emit('change', { sort: v })"
      >
        <el-option v-for="(label, val) in SORT_LABELS" :key="val" :label="label" :value="val" />
      </el-select>

      <el-date-picker
        :model-value="query.date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="按日期"
        clearable
        class="note-filter-bar__date"
        @change="(v: string | null) => emit('change', { date: v || undefined })"
      />
    </div>
  </div>
</template>

<style scoped>
.note-filter-bar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
}
.note-filter-bar__sel {
  width: 140px;
}
.note-filter-bar__date {
  width: 160px;
}
</style>
