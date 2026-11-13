<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listNotes } from '@/api/note'
import { plainSummary } from '@/utils/richText'
import { weekRangeLabel } from '@/utils/date'
import type { NoteBrief } from '@/types/note'

interface SearchItem extends NoteBrief {
  /** el-autocomplete 用于显示/回填的字段 */
  value: string
}

const emit = defineEmits<{
  (e: 'open', weekStart: string): void
}>()

const keyword = ref('')
let seq = 0

/** 不带日期范围，按关键词搜索全部周记录；只展示前 20 条。 */
async function fetchSuggestions(
  query: string,
  cb: (items: SearchItem[]) => void,
): Promise<void> {
  const q = query.trim()
  const current = ++seq
  if (!q) {
    cb([])
    return
  }
  try {
    const data = await listNotes({
      scope: 'mine',
      period: 'WEEK',
      keyword: q,
      sort: 'log_date_desc',
      page: 1,
      size: 20,
    })
    if (current !== seq) return
    cb(
      (data.list ?? [])
        .filter((n) => n.logDate)
        .map((n) => ({ ...n, value: weekRangeLabel(n.logDate as string) })),
    )
  } catch {
    if (current === seq) cb([])
  }
}

function onSelect(item: Record<string, unknown>): void {
  const weekStart = item.logDate
  keyword.value = ''
  if (typeof weekStart === 'string' && weekStart) emit('open', weekStart)
}

function summaryOf(item: SearchItem): string {
  return plainSummary(item.contentSummary) || '暂无内容'
}
</script>

<template>
  <el-autocomplete
    v-model="keyword"
    class="weekly-note-search"
    data-test="weekly-search"
    :fetch-suggestions="fetchSuggestions"
    :trigger-on-focus="false"
    :debounce="300"
    :prefix-icon="Search"
    clearable
    placeholder="搜索所有周记录"
    popper-class="weekly-note-search__popper"
    @select="onSelect"
  >
    <template #default="{ item }">
      <div class="weekly-note-search__item">
        <span class="weekly-note-search__range">{{ item.value }}</span>
        <span class="weekly-note-search__summary">{{ summaryOf(item) }}</span>
      </div>
    </template>
  </el-autocomplete>
</template>

<style scoped>
.weekly-note-search {
  width: 100%;
}
</style>

<style>
.weekly-note-search__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0;
  line-height: 1.4;
}
.weekly-note-search__range {
  font-size: 13px;
  color: var(--bb-text-primary);
}
.weekly-note-search__summary {
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  color: var(--bb-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
