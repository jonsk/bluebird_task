<script setup lang="ts">
import { DocumentDelete } from '@element-plus/icons-vue'

/** 通用 404 空态。 */
withDefaults(defineProps<{ text?: string }>(), { text: '未找到该工作日志' })
</script>

<template>
  <div class="note-not-found">
    <el-icon class="note-not-found__icon"><DocumentDelete /></el-icon>
    <p class="note-not-found__text">{{ text }}</p>
  </div>
</template>

<style scoped>
.note-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--bb-text-muted);
}
.note-not-found__icon {
  font-size: 48px;
}
.note-not-found__text {
  margin: 0;
  font-size: 14px;
}
</style>
