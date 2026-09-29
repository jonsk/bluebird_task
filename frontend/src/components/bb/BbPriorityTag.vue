<script setup lang="ts">
import { computed } from 'vue'
import { PRIORITY_LABEL, type Priority } from '@/utils/constants'

/** 优先级标签（LOW/MEDIUM/HIGH/URGENT，01 §8.1）。 */
const props = defineProps<{ priority?: Priority | null }>()

const label = computed(() => (props.priority ? PRIORITY_LABEL[props.priority] : ''))
const type = computed(() => {
  switch (props.priority) {
    case 'URGENT':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'LOW':
      return 'info'
    default:
      return ''
  }
})
</script>

<template>
  <el-tag v-if="priority" size="small" :type="type as never" effect="light" round>{{ label }}</el-tag>
</template>
