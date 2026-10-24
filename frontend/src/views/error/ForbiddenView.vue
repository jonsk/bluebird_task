<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BbButton from '@/components/bb/BbButton.vue'

const router = useRouter()
const route = useRoute()

/**
 * 守卫跳转时带 `?from=`（被拦截的原始路径）。
 * 只用于**展示**，不做「返回到 from」——那会立刻再次被守卫拦回 403，形成死循环。
 */
const from = computed(() => {
  const v = route.query.from
  return typeof v === 'string' && v.startsWith('/') ? v : ''
})

function goHome(): void {
  void router.replace('/index')
}
</script>

<template>
  <div class="error-page">
    <h1>403</h1>
    <p>无权限访问该页面</p>
    <p v-if="from" class="error-page__from">被拦截的地址：{{ from }}</p>
    <BbButton type="primary" @click="goHome">返回首页</BbButton>
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
.error-page__from {
  margin: 0;
  font-size: 12px;
  color: var(--bb-color-muted);
}
</style>
