<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BbEmpty from '@/components/bb/BbEmpty.vue'
import { ROUTE_SCOPE_MAP, type TaskScope } from '@/utils/constants'

/**
 * 统一任务视图（03 §5.3.1）：按 route.name 经 ROUTE_SCOPE_MAP 取 scope（R9-契约）。
 * M0：占位；M2 接入 task store + BbTaskList/BbTaskComposer/BbTaskCard。
 */
const route = useRoute()
const scope = computed<TaskScope>(() => ROUTE_SCOPE_MAP[String(route.name)] ?? 'all')
const title = computed(() => (route.meta.title as string) ?? '任务')
</script>

<template>
  <div class="task-view">
    <header class="task-view__header">
      <h2>{{ title }}</h2>
      <span class="task-view__scope">scope: {{ scope }}</span>
    </header>
    <BbEmpty description="任务列表将在 M2 接入" />
  </div>
</template>

<style scoped>
.task-view {
  padding: 16px 20px;
}
.task-view__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.task-view__header h2 {
  margin: 0;
  font-size: 18px;
}
.task-view__scope {
  color: var(--bb-color-muted);
  font-size: 12px;
}
</style>
