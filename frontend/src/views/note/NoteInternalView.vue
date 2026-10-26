<script setup lang="ts">
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/** 本部门日志：本级部门 + 全部子部门（父看子、子不看父）；无部门时后端返回空列表。 */
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
} = useNoteScopePage({ emptyText: '本部门暂无工作日志' })
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
