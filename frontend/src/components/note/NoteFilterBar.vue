<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { NoteQuery, NoteQueryPatch, NoteScope, NoteSort } from '@/types/note'

/**
 * NoteFilterBar — 关键词（+ 排序 / 日期）。
 * 所有变更 emit('change', patch) 由外壳（useNoteList）统一写入；本组件不请求。
 * 标签能力已下线，不再有标签筛选。
 * 「全部/置顶」开关随 pinned 能力整体下线已移除。
 * 「我的日志」(scope=mine) 仅保留关键词搜索；其余页面（员工/全部）另含排序与日期。
 */
const props = withDefaults(
  defineProps<{
    query: NoteQuery
    loading?: boolean
    scope: NoteScope
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

const keyword = ref(props.query.keyword ?? '')
let kwTimer: ReturnType<typeof setTimeout> | undefined
function onKeyword(val: string): void {
  keyword.value = val
  if (kwTimer) clearTimeout(kwTimer)
  kwTimer = setTimeout(() => emit('change', { keyword: val.trim() || undefined }), 300)
}
watch(
  () => props.query.keyword,
  (v) => {
    if (v !== keyword.value) keyword.value = v ?? ''
  },
)
</script>

<template>
  <div class="note-filter-bar" data-test="note-filter-bar">
    <div class="note-filter-bar__row">
      <el-input
        :model-value="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索关键词"
        class="note-filter-bar__kw"
        @input="onKeyword"
        @clear="onKeyword('')"
      />

      <el-select
        v-if="scope !== 'mine'"
        :model-value="query.sort ?? 'log_date_desc'"
        class="note-filter-bar__sel"
        @change="(v: NoteSort) => emit('change', { sort: v })"
      >
        <el-option v-for="(label, val) in SORT_LABELS" :key="val" :label="label" :value="val" />
      </el-select>

      <el-date-picker
        v-if="scope !== 'mine'"
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
.note-filter-bar__kw {
  width: 220px;
}
.note-filter-bar__sel {
  width: 140px;
}
.note-filter-bar__date {
  width: 160px;
}
</style>
