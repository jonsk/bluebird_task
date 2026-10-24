<script setup lang="ts">
import { ArrowRight, StarFilled } from '@element-plus/icons-vue'
import type { NoteBrief } from '@/types/note'

/** NoteMiniList — 右栏小列表（无分页），底部「查看全部」跳转。 */
withDefaults(
  defineProps<{
    notes: NoteBrief[]
    loading?: boolean
    activeId?: number | null
  }>(),
  { loading: false, activeId: null },
)

const emit = defineEmits<{
  (e: 'open', id: number): void
  (e: 'view-all'): void
}>()
</script>

<template>
  <div class="note-mini-list">
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-empty v-else-if="!notes.length" description="今日暂无日志" :image-size="48" />
    <ul v-else class="note-mini-list__items">
      <li
        v-for="note in notes"
        :key="note.id"
        class="note-mini-list__item"
        :class="{ 'is-active': activeId != null && note.id === activeId }"
        @click="note.id != null && emit('open', note.id)"
      >
        <span class="note-mini-list__date">{{ note.logDate }}</span>
        <span class="note-mini-list__summary">{{ note.contentSummary || '（无内容）' }}</span>
        <el-icon v-if="note.pinned" class="note-mini-list__pin"><StarFilled /></el-icon>
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
.note-mini-list__pin {
  color: #b88230;
}
.note-mini-list__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
</style>
