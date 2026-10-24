<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/**
 * 归档区单一组件（无 NoteArchivedAllView）：
 * `/notes/archived` = mine+archived；`/notes/archived/all` = all+archived（仅 ADMIN）。
 * 两段即两路由，返回/刷新天然保留分段；列表数据完全由 route.meta 驱动。
 */
const route = useRoute()
const isAll = computed(() => route.meta.scope === 'all')

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
} = useNoteScopePage({
  emptyText: '归档区为空',
  adminOnly: () => isAll.value,
  forbiddenText: '仅管理员可查看全部归档',
})
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
