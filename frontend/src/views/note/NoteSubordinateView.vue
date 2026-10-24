<script setup lang="ts">
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/**
 * 下属日志：我是部门负责人时可读下属（含子部门）日志。
 * 开关关闭时后端返回 403 → 本页渲染空态（不给重试按钮），导航入口同时隐藏。
 */
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
  emptyText: '下属暂无工作日志',
  emptyOn403: true,
  forbiddenText: '未开启「上级可读下属日志」，或您不是部门负责人',
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
