<script setup lang="ts">
import { WarningFilled, RefreshRight } from '@element-plus/icons-vue'
import type { NoteBrief, NotePeriod } from '@/types/note'
import NoteCard from './NoteCard.vue'

/**
 * NoteList — 纯容器：loading / error+retry / empty / 分页 / 列表渲染。
 * 不含 composer / 筛选器 / scope。事件透传给父。
 */
withDefaults(
  defineProps<{
    notes: NoteBrief[]
    loading?: boolean
    error?: Error | null
    page: number
    total: number
    activeId?: number | null
    emptyText?: string
    period?: NotePeriod
  }>(),
  {
    loading: false,
    error: null,
    activeId: null,
    emptyText: '暂无工作日志',
    period: 'DAY',
  },
)

const emit = defineEmits<{
  (e: 'page-change', page: number): void
  (e: 'retry'): void
  (e: 'open', id: number): void
  (e: 'edit', id: number): void
  (e: 'archive', id: number): void
  (e: 'unarchive', id: number): void
  (e: 'remove', id: number): void
}>()
</script>

<template>
  <div class="note-list">
    <!-- 首屏才用骨架屏；已有数据时后台刷新保持列表原样，避免高度抖动/光标跳动 -->
    <div v-if="loading && !notes.length" class="note-list__loading">
      <el-skeleton :rows="4" animated />
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="error" class="note-list__error" data-test="note-list-error">
      <el-icon class="note-list__error-icon"><WarningFilled /></el-icon>
      <span class="note-list__error-text">加载失败：{{ error.message || '请重试' }}</span>
      <el-button size="small" :icon="RefreshRight" @click="emit('retry')">重试</el-button>
    </div>

    <el-empty v-else-if="!notes.length" :description="emptyText" data-test="note-list-empty" />

    <template v-else>
      <NoteCard
        v-for="note in notes"
        :key="note.id"
        :note="note"
        :period="period"
        :active="activeId != null && note.id === activeId"
        @open="emit('open', $event)"
        @edit="emit('edit', $event)"
        @archive="emit('archive', $event)"
        @unarchive="emit('unarchive', $event)"
        @remove="emit('remove', $event)"
      />
      <el-pagination
        class="note-list__pager"
        layout="prev, pager, next"
        :current-page="page"
        :total="total"
        :page-size="20"
        hide-on-single-page
        @current-change="(p: number) => emit('page-change', p)"
      />
    </template>
  </div>
</template>

<style scoped>
.note-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.note-list__loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;
}
.note-list__error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px;
  color: var(--el-color-danger);
}
.note-list__error-icon {
  font-size: 20px;
}
.note-list__error-text {
  font-size: 13px;
}
.note-list__pager {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}
</style>
