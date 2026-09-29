<script setup lang="ts">
import BbEmpty from '@/components/bb/BbEmpty.vue'

/** 操作/登录日志（03 §5.6，ADMIN/AUDITOR）。M0 占位，M4 接入 audit 接口。 */
</script>

<template>
  <div class="admin-audit">
    <h2>操作/登录日志</h2>
    <BbEmpty description="审计日志将在 M4 接入（audit 接口）" />
  </div>
</template>

<style scoped>
.admin-audit {
  padding: 16px 20px;
}
</style>
