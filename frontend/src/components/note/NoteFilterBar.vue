<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import type { NoteQuery, NoteQueryPatch, NoteScope, NoteVisibility, NoteSort } from '@/types/note'
import type { TagOption } from '@/types/tag'
import BbTagPicker from '@/components/bb/BbTagPicker.vue'

/**
 * NoteFilterBar — 关键词 / 标签 / 可见性 / 置顶 / 排序 / 日期。
 * 所有变更 emit('change', patch) 由外壳（useNoteList）统一写入；本组件不请求。
 */
const props = withDefaults(
  defineProps<{
    query: NoteQuery
    loading?: boolean
    scope: NoteScope
    tagOptions?: TagOption[]
    tagLoading?: boolean
    tagError?: boolean
  }>(),
  { loading: false, tagOptions: undefined, tagLoading: false, tagError: false },
)

const emit = defineEmits<{
  (e: 'change', patch: NoteQueryPatch): void
  (e: 'retry-tags'): void
}>()

const VISIBILITY_LABELS: Record<NoteVisibility, string> = {
  PRIVATE: '私有',
  INTERNAL: '部门内',
  PUBLIC: '公开',
}
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

// 标签：BbTagPicker 多选（number[]），API 仅支持单 tagId，取最后选择项作为筛选条件。
const tagIds = computed<number[]>(() => (props.query.tagId != null ? [props.query.tagId] : []))
function onTags(val: number[]): void {
  emit('change', { tagId: val.length ? val[val.length - 1] : undefined })
}

const pinnedChecked = computed(() => Boolean(props.query.pinned))
function onPinned(v: boolean | string | number): void {
  emit('change', { pinned: v ? true : undefined })
}
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

      <BbTagPicker
        v-if="!tagError"
        :options="tagOptions"
        :model-value="tagIds"
        :disabled="loading"
        class="note-filter-bar__tags"
        @update:model-value="onTags"
      />
      <div v-else class="note-filter-bar__tag-error">
        <span>标签加载失败</span>
        <el-button size="small" :icon="RefreshRight" @click="emit('retry-tags')">重试</el-button>
      </div>

      <el-select
        :model-value="query.visibility"
        clearable
        placeholder="可见性"
        class="note-filter-bar__sel"
        @change="(v: NoteVisibility | undefined) => emit('change', { visibility: v || undefined })"
      >
        <el-option v-for="(label, val) in VISIBILITY_LABELS" :key="val" :label="label" :value="val" />
      </el-select>

      <el-switch
        :model-value="pinnedChecked"
        inline-prompt
        active-text="置顶"
        inactive-text="全部"
        class="note-filter-bar__pin"
        @change="onPinned"
      />

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
.note-filter-bar__kw {
  width: 220px;
}
.note-filter-bar__tags {
  width: 240px;
}
.note-filter-bar__sel {
  width: 140px;
}
.note-filter-bar__date {
  width: 160px;
}
.note-filter-bar__tag-error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-color-danger);
}
</style>
