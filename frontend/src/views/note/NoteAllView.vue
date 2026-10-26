<script setup lang="ts">
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/** 全部日志（仅 ADMIN）：不分 visibility；页面级 403 空态兜底角色缓存过期。 */
const {
  notes,
  query,
  total,
  pageNo,
  loading,
  error,
  tagOptions,
  tagError,
  facets,
  errorMode,
  emptyText,
  list,
  onOpen,
  onEdit,
  onPin,
  onUnpin,
  onArchive,
  onUnarchive,
  onRemove,
} = useNoteScopePage({ emptyText: '暂无工作日志', adminOnly: true })
</script>

<template>
  <NoteListView
    :notes="notes"
    :query="query"
    :page="pageNo"
    :total="total"
    :loading="loading"
    :error="error"
    :tag-options="tagOptions"
    :tag-loading="facets.loading.value"
    :tag-error="tagError"
    :error-mode="errorMode"
    :empty-text="emptyText"
    @change="list.setQuery"
    @retry-tags="facets.retry"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @pin="onPin"
    @unpin="onUnpin"
    @archive="onArchive"
    @unarchive="onUnarchive"
    @remove="onRemove"
  />
</template>
