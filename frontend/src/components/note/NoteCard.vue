<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Edit } from '@element-plus/icons-vue'
import { stripMarkdown } from '@/utils/markdown'
import { weekRangeLabel } from '@/utils/date'
import type { NoteBrief, NotePeriod } from '@/types/note'

/**
 * NoteCard — 列表卡片（只展示，不请求）。
 * 点击卡片 → open；canEdit / canManage 决定操作按钮。
 * 用于所有日志页，受控于调用方（不显示归档按钮）。
 */
const props = withDefaults(
  defineProps<{ note: NoteBrief; active?: boolean; period?: NotePeriod }>(),
  { active: false, period: 'DAY' },
)

/** 周记录 logDate 是周一，卡片上展示整周区间。 */
const dateLabel = computed(() => {
  const date = props.note.logDate ?? ''
  return props.period === 'WEEK' && date ? weekRangeLabel(date) : date
})

const emit = defineEmits<{
  (e: 'open', id: number): void
  (e: 'edit', id: number): void
  (e: 'remove', id: number): void
}>()

function onClick(): void {
  if (props.note.id != null) emit('open', props.note.id)
}
function stop(e: Event): void {
  e.stopPropagation()
}
</script>

<template>
  <div
    class="note-card"
    :class="{ 'is-active': active }"
    :data-test="`note-card-${note.id}`"
    @click="onClick"
  >
    <div class="note-card__head">
      <span class="note-card__date">{{ dateLabel }}</span>
      <span class="note-card__owner">{{ note.ownerName }}</span>
    </div>

    <p class="note-card__summary">{{ stripMarkdown(note.contentSummary) || (period === 'WEEK' ? '本周暂无内容' : '（无内容）') }}</p>

    <div v-if="note.canEdit || note.canManage" class="note-card__ops" @click="stop">
      <el-button
        v-if="note.canEdit"
        text
        size="small"
        :icon="Edit"
        title="编辑"
        data-test="note-card-edit"
        @click="emit('edit', note.id!)"
      />
      <el-button
        v-if="note.canManage"
        text
        size="small"
        type="danger"
        :icon="Delete"
        title="删除"
        data-test="note-card-delete"
        @click="emit('remove', note.id!)"
      />
    </div>
  </div>
</template>

<style scoped>
.note-card {
  position: relative;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.note-card:hover {
  box-shadow: var(--bb-shadow-flat);
}
.note-card.is-active {
  border-color: var(--bg-accent);
}
.note-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-card__owner {
  margin-left: auto;
}
.note-card__summary {
  margin: 8px 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--bb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 6.4em;
  overflow: hidden;
}
.note-card__ops {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 6px;
}
</style>
