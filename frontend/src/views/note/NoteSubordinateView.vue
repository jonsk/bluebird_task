<script setup lang="ts">
import { useRoute } from 'vue-router'
import NoteListView from './NoteListView.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'

/**
 * 员工日志/员工周记录：不是部门负责人时后端 403 → 页面渲染空态（不显示重试按钮）。
 * 同一组件服务日/周两个入口，靠 `route.meta.period` 切换文案与查询口径。
 */
const isWeekly = useRoute().meta.period === 'WEEK'

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
  onRemove,
} = useNoteScopePage({
  emptyText: isWeekly ? '员工本周暂无工作记录' : '员工暂无工作日志',
  emptyOn403: true,
  forbiddenText: isWeekly
    ? '无权查看员工周记录：仅部门负责人可读下属记录'
    : '无权查看员工日志：仅部门负责人可读下属日志',
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
    :error-mode="errorMode"
    :empty-text="emptyText"
    @change="list.setQuery"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @remove="onRemove"
  />
</template>
