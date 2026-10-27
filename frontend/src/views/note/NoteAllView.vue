<script setup lang="ts">
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/** 全部日志（后台 /admin/notes，仅 ADMIN）：页面级 403 空态兜底角色缓存过期。 */
const {
  notes,
  query,
  total,
  pageNo,
  loading,
  error,
  errorMode,
  emptyText,
  list,
  onOpen,
  onEdit,
  onPin,
  onUnpin,
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
    :error-mode="errorMode"
    :empty-text="emptyText"
    @change="list.setQuery"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @pin="onPin"
    @unpin="onUnpin"
    @remove="onRemove"
  />
</template>
