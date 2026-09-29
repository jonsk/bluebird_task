<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BbFilterRail from '@/components/bb/BbFilterRail.vue'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'

/**
 * 唯一布局（三栏，03 §5.2）：左栏 BbFilterRail（六视图计数 + 分类树 + 自定义栏）
 * + 中栏 router-view + 右栏抽屉由 TaskView 内嵌。
 */
const auth = useAuthStore()
const taskStore = useTaskStore()
const router = useRouter()

onMounted(() => {
  void taskStore.fetchCounts()
})

async function onLogout(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="layout__brand">BlueBird 任务</div>
      <div class="layout__user">
        <span>{{ auth.user?.name ?? auth.user?.username ?? '未登录' }}</span>
        <span class="layout__role">{{ auth.roleCode }}</span>
        <a class="layout__logout" @click="onLogout">退出</a>
      </div>
    </header>

    <div class="layout__body">
      <BbFilterRail />

      <main class="layout__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--bb-header-height);
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #eef0f3;
}
.layout__brand {
  font-weight: 600;
  color: var(--bb-color-primary);
}
.layout__user {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.layout__role {
  color: var(--bb-color-muted);
}
.layout__logout {
  color: var(--bb-color-primary);
  cursor: pointer;
}
.layout__body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.layout__main {
  flex: 1;
  min-width: 0;
  overflow: auto;
}
</style>
