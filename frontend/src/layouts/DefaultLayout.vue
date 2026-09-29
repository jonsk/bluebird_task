<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'

/**
 * 唯一布局（三栏，03 §5.2）：左栏导航（含各视图计数）+ 中栏 router-view + 右栏抽屉由 TaskView 内嵌。
 */
const auth = useAuthStore()
const taskStore = useTaskStore()
const route = useRoute()
const router = useRouter()

const navItems = [
  { name: 'index', label: '我的一天', countKey: 'day' },
  { name: 'myWeek', label: '未来 7 天', countKey: 'week' },
  { name: 'myJoin', label: '我@Ta的', countKey: 'joined' },
  { name: 'myDo', label: '分配给我的', countKey: 'assigned' },
  { name: 'myCollect', label: '我的收藏', countKey: 'collect' },
  { name: 'allTask', label: '全部任务', countKey: 'all' },
] as const

const canManageUsers = computed(() => auth.hasRole('ADMIN', 'USER_MANAGER'))
const canViewAudit = computed(() => auth.hasRole('ADMIN', 'AUDITOR'))
const currentName = computed(() => String(route.name ?? ''))
const counts = computed(() => taskStore.counts)

function countOf(key: string): number | null {
  const c = counts.value
  if (!c) return null
  return Number((c as unknown as Record<string, number>)[key] ?? 0)
}

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
      <aside class="layout__left">
        <nav>
          <router-link
            v-for="item in navItems"
            :key="item.name"
            :to="{ name: item.name }"
            class="layout__nav-item"
            :class="{ 'is-active': currentName === item.name }"
          >
            <span>{{ item.label }}</span>
            <span v-if="countOf(item.countKey) !== null" class="layout__count">{{ countOf(item.countKey) }}</span>
          </router-link>
        </nav>

        <div class="layout__nav-group">
          <router-link :to="{ name: 'calendar' }" class="layout__nav-item" :class="{ 'is-active': currentName === 'calendar' }">
            日历
          </router-link>
        </div>

        <div class="layout__nav-group" v-if="canManageUsers || canViewAudit">
          <router-link v-if="canManageUsers" :to="{ name: 'adminUsers' }" class="layout__nav-item" :class="{ 'is-active': currentName === 'adminUsers' }">
            用户管理
          </router-link>
          <router-link v-if="canManageUsers" :to="{ name: 'adminDepts' }" class="layout__nav-item" :class="{ 'is-active': currentName === 'adminDepts' }">
            部门管理
          </router-link>
          <router-link v-if="canViewAudit" :to="{ name: 'adminAudit' }" class="layout__nav-item" :class="{ 'is-active': currentName === 'adminAudit' }">
            操作/登录日志
          </router-link>
        </div>
      </aside>

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
.layout__left {
  width: var(--bb-sidebar-left-width);
  padding: 12px;
  background: #fff;
  border-right: 1px solid #eef0f3;
  overflow: auto;
}
.layout__nav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-radius: var(--bb-radius);
  color: #1f2430;
  text-decoration: none;
  font-size: 14px;
}
.layout__nav-item:hover {
  background: #f5f6f8;
}
.layout__nav-item.is-active {
  background: #eef3ff;
  color: var(--bb-color-primary);
}
.layout__count {
  min-width: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--bb-color-muted);
}
.layout__nav-group {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #eef0f3;
}
.layout__main {
  flex: 1;
  min-width: 0;
  overflow: auto;
}
</style>
