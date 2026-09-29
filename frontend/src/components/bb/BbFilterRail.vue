<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BbCategoryTree from './BbCategoryTree.vue'
import BbCustomMenu from './BbCustomMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'

/**
 * BbFilterRail — 左栏（03 §5.2）：固定六视图（含计数）+ 分类树 + 自定义栏。
 * 分类/自定义栏选中后写 task store 的筛选态，由 TaskView 监听重查（补齐旧实现缺失的联动）。
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

/** 选中分类 → 与当前视图/关键字叠加过滤。 */
function onSelectCategory(id: number | null): void {
  taskStore.setCategoryFilter(id)
}

/** 选中自定义栏 → 该栏是独立视图，切到「全部任务」再按 menuId 过滤。 */
function onSelectMenu(id: number | null): void {
  taskStore.setMenuFilter(id)
  if (id != null && route.name !== 'allTask') {
    void router.push({ name: 'allTask' })
  }
}
</script>

<template>
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

    <div class="layout__nav-group">
      <BbCategoryTree @select="onSelectCategory" />
    </div>

    <div class="layout__nav-group">
      <BbCustomMenu @select="onSelectMenu" />
    </div>
  </aside>
</template>

<style scoped>
.layout__left {
  /* 固定宽度：flex 项默认 min-width:auto 会被内部树内容撑宽（曾把主栏右移 25px） */
  flex: 0 0 var(--bb-sidebar-left-width);
  width: var(--bb-sidebar-left-width);
  min-width: 0;
  box-sizing: border-box;
  padding: 12px;
  background: #fff;
  border-right: 1px solid #eef0f3;
  overflow-y: auto;
  overflow-x: hidden;
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
</style>
