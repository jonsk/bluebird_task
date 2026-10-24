<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listNotes } from '@/api/note'
import { searchTasks } from '@/api/task'
import type { NoteTargetRef } from '@/types/note'

/**
 * NoteRelationPicker — 引用选择器（note ↔ task）。
 * - note：`GET /notes?scope=visible&keyword=`（同一 isVisible 语义，仅可见日志）
 * - task：`GET /tasks/search`（PageResult 风格；keyword < 2 字符后端返回空列表）
 * - 已引用项禁用（`disabledRefs`），防重复引用；不返回自身（`excludeNoteId`）。
 */
const visible = defineModel<boolean>({ required: true })

const props = withDefaults(
  defineProps<{
    /** 默认 Tab：引用日志 / 引用任务 */
    defaultMode?: 'NOTE' | 'TASK'
    /** 当前日志 id（排除自引） */
    excludeNoteId?: number | null
    /** 已引用的目标（禁用重复选择） */
    disabledRefs?: NoteTargetRef[]
  }>(),
  { defaultMode: 'NOTE', excludeNoteId: null, disabledRefs: () => [] },
)

const emit = defineEmits<{ (e: 'pick', ref: NoteTargetRef & { summary?: string }): void }>()

const mode = ref<'NOTE' | 'TASK'>(props.defaultMode)
const keyword = ref('')
const loading = ref(false)
const rows = ref<Array<{ id: number; summary: string; sub?: string }>>([])
let seq = 0

const disabledKeys = computed(() => new Set((props.disabledRefs ?? []).map((r) => `${r.targetType}:${r.targetId}`)))

function isDisabled(id: number): boolean {
  return disabledKeys.value.has(`${mode.value}:${id}`)
}

async function search(): Promise<void> {
  const s = ++seq
  const kw = keyword.value.trim()
  if (mode.value === 'TASK' && kw.length < 2) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    if (mode.value === 'NOTE') {
      const data = await listNotes({ scope: 'visible', keyword: kw || undefined, page: 1, size: 20 })
      if (s !== seq) return
      rows.value = (data.list ?? [])
        .filter((n) => Number(n.id) !== Number(props.excludeNoteId))
        .map((n) => ({
          id: Number(n.id),
          summary: n.contentSummary || '（无内容）',
          sub: `${n.logDate ?? ''} ${n.ownerName ?? ''}`.trim(),
        }))
    } else {
      const data = await searchTasks({ keyword: kw, page: 1, size: 20 })
      if (s !== seq) return
      rows.value = (data.list ?? []).map((t) => ({
        id: Number(t.id),
        summary: t.title || '（无标题）',
        sub: t.status ? String(t.status) : undefined,
      }))
    }
  } catch {
    if (s !== seq) return
    rows.value = []
  } finally {
    if (s === seq) loading.value = false
  }
}

watch(visible, (open) => {
  if (!open) return
  mode.value = props.defaultMode
  keyword.value = ''
  void search()
})
watch(mode, () => {
  keyword.value = ''
  rows.value = []
})

function pick(row: { id: number; summary: string }): void {
  if (isDisabled(row.id)) return
  emit('pick', { targetType: mode.value, targetId: row.id, summary: row.summary })
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="添加引用" width="620px" append-to-body data-test="note-relation-picker">
    <el-tabs v-model="mode" class="nr-tabs">
      <el-tab-pane label="引用日志" name="NOTE" />
      <el-tab-pane label="引用任务" name="TASK" />
    </el-tabs>

    <div class="nr-search">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        :placeholder="mode === 'NOTE' ? '搜索可见日志内容' : '搜索任务标题（至少 2 个字）'"
        data-test="relation-keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <div v-loading="loading" class="nr-list">
      <div v-if="!rows.length" class="nr-empty">
        {{ mode === 'TASK' && keyword.trim().length < 2 ? '请输入至少 2 个字' : '没有匹配结果' }}
      </div>
      <div
        v-for="row in rows"
        :key="row.id"
        class="nr-row"
        :class="{ 'is-disabled': isDisabled(row.id) }"
        :data-test="`relation-row-${row.id}`"
        @click="pick(row)"
      >
        <span class="nr-row__summary">{{ row.summary }}</span>
        <span class="nr-row__sub">{{ isDisabled(row.id) ? '已引用' : row.sub }}</span>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.nr-tabs {
  margin-bottom: 8px;
}
.nr-search {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.nr-list {
  min-height: 180px;
  max-height: 360px;
  overflow-y: auto;
}
.nr-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--bb-text-muted);
}
.nr-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  font-size: 13px;
}
.nr-row:hover {
  background: var(--bg-hover);
}
.nr-row.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.nr-row__summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--bb-text-secondary);
}
.nr-row__sub {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--bb-text-muted);
}
</style>
