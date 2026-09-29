<script setup lang="ts">
import { useRouter } from 'vue-router'
import BbButton from '@/components/bb/BbButton.vue'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <h1>403</h1>
    <p>无权限访问该页面</p>
    <BbButton type="primary" @click="router.replace('/index')">返回首页</BbButton>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
}
.error-page h1 {
  font-size: 64px;
  margin: 0;
  color: var(--bb-color-muted);
}
</style>
