<script setup lang="ts">
/**
 * BbEmpty — 空态占位原子（契约：BbEmpty.md）。
 */
withDefaults(defineProps<{ description?: string; image?: string }>(), {
  description: '暂无数据',
})
</script>

<template>
  <div class="bb-empty">
    <el-empty :description="description" :image="image" />
    <slot />
  </div>
</template>

<style scoped>
.bb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
}
</style>
