<script setup lang="ts">
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/** 被授权日志：他人授权给我的日志；编辑能力按 grant 角色（EDITOR）由后端下发的 canEdit 决定。 */
const {
  notes,
  query,
  total,
  pageNo,
  loading,
  error,
  tagOptions,
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
} = useNoteScopePage({ emptyText: '暂无被授权的工作日志' })
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
    :tag-error="facets.error.value != null"
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
