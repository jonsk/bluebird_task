<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Connection,
  DataBoard,
  Document,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { logoutEverywhere } from '@/utils/logout'

/**
 * 后台独立布局（2026-10-18 用户拍板）：与前台 DefaultLayout 完全分离，
 * 左侧固定导航 + 顶部标题条，不带任务左栏（BbFilterRail），后台更清爽。
 *
 * 可见性：菜单按角色过滤 —— 只有 ADMIN 能看到 Casdoor 对接与常规设置
 * （后端 `AdminConfigController` 已 `@PreAuthorize("hasRole('ADMIN')")`）；
 * 用户与机构额外开放给 USER_MANAGER，日志额外开放给 AUDITOR。
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

interface AdminNavItem {
  name: string
  path: string
  label: string
  icon: unknown
  roles: Array<'ADMIN' | 'AUDITOR' | 'USER_MANAGER'>
}

const ALL_NAV: AdminNavItem[] = [
  { name: 'adminOverview', path: '/admin', label: '概览', icon: DataBoard, roles: ['ADMIN', 'AUDITOR', 'USER_MANAGER'] },
  { name: 'adminUsers', path: '/admin/users', label: '用户与机构', icon: User, roles: ['ADMIN', 'USER_MANAGER'] },
  { name: 'adminCasdoor', path: '/admin/casdoor', label: 'Casdoor 对接', icon: Connection, roles: ['ADMIN'] },
  { name: 'adminLogs', path: '/admin/logs', label: '日志', icon: Document, roles: ['ADMIN', 'AUDITOR'] },
  { name: 'adminSettings', path: '/admin/settings', label: '常规设置', icon: Setting, roles: ['ADMIN'] },
]

const navItems = computed(() => ALL_NAV.filter((item) => auth.hasRole(...item.roles)))

const currentName = computed(() => String(route.name ?? ''))
const pageTitle = computed(() => ALL_NAV.find((i) => i.name === currentName.value)?.label ?? '后台管理')
const displayName = computed(() => auth.user?.name ?? auth.user?.username ?? '未登录')
const userName = computed(() => auth.user?.username ?? '')

function go(path: string): void {
  if (route.path !== path) void router.push(path)
}

async function backToWorkspace(): Promise<void> {
  await router.push('/index')
}

async function onLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认退出登录？', '退出登录', { type: 'warning', confirmButtonText: '退出' })
  } catch {
    return
  }
  await logoutEverywhere(router)
}
</script>

<template>
  <div class="admin">
    <aside class="admin__side">
      <div class="admin__brand">
        <span class="admin__brand-dot"></span>
        <div class="admin__brand-text">
          <p class="admin__brand-title">蓝鸟任务</p>
          <p class="admin__brand-sub">管理后台</p>
        </div>
      </div>

      <nav class="admin__nav">
        <button
          v-for="item in navItems"
          :key="item.name"
          type="button"
          class="admin__nav-item"
          :class="{ 'is-active': currentName === item.name }"
          :data-test="`admin-nav-${item.name}`"
          @click="go(item.path)"
        >
          <el-icon class="admin__nav-icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="admin__side-foot">
        <button type="button" class="admin__back" data-test="admin-back" @click="backToWorkspace">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回工作台</span>
        </button>
      </div>
    </aside>

    <div class="admin__body">
      <header class="admin__top">
        <h1 class="admin__title">{{ pageTitle }}</h1>
        <div class="admin__user">
          <span class="admin__user-name">{{ displayName }}</span>
          <span class="admin__user-account">{{ userName }}</span>
          <el-tooltip content="退出登录" placement="bottom" effect="light">
            <el-icon class="admin__logout" data-test="admin-logout" @click="onLogout"><SwitchButton /></el-icon>
          </el-tooltip>
        </div>
      </header>

      <main class="admin__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-secondary);
}
.admin__side {
  display: flex;
  flex: 0 0 208px;
  flex-direction: column;
  width: 208px;
  height: 100vh;
  box-sizing: border-box;
  background: var(--bg-primary);
  box-shadow: 0.5px 0 0 0 var(--bg-separator);
}
.admin__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--bb-title-height);
  padding: 0 16px;
  box-sizing: border-box;
  box-shadow: 0 0.5px 0 0 var(--bg-separator);
}
.admin__brand-dot {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--bb-color-primary);
}
.admin__brand-text {
  min-width: 0;
}
.admin__brand-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--bb-text-primary);
}
.admin__brand-sub {
  margin: 0;
  font-size: 11px;
  line-height: 1.2;
  color: var(--bb-text-muted);
}
.admin__nav {
  flex: 1;
  min-height: 0;
  padding: 8px 0;
  overflow-y: auto;
}
.admin__nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 40px;
  padding: 0 16px;
  border: 0;
  background: transparent;
  font-size: 14px;
  color: var(--bb-text-secondary);
  text-align: left;
  cursor: pointer;
}
.admin__nav-item:hover {
  background: var(--bg-hover);
}
.admin__nav-item.is-active {
  background: var(--bg-active);
  color: var(--bb-color-primary);
  font-weight: 700;
}
.admin__nav-icon {
  font-size: 17px;
}
.admin__side-foot {
  flex: 0 0 48px;
  padding: 0 8px;
  box-shadow: 0 -0.5px 0 0 var(--bg-separator);
}
.admin__back {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 40px;
  margin-top: 4px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  font-size: 13px;
  color: var(--bb-text-muted);
  cursor: pointer;
}
.admin__back:hover {
  background: var(--bg-hover);
  color: var(--bb-text-secondary);
}
.admin__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
}
.admin__top {
  display: flex;
  flex: 0 0 var(--bb-title-height);
  align-items: center;
  justify-content: space-between;
  height: var(--bb-title-height);
  padding: 0 20px;
  box-sizing: border-box;
  background: var(--bg-primary);
  box-shadow: 0 0.5px 0 0 var(--bg-separator);
}
.admin__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--bb-text-primary);
}
.admin__user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.admin__user-name {
  font-size: 13px;
  color: var(--bb-text-secondary);
}
.admin__user-account {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.admin__logout {
  font-size: 17px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.admin__logout:hover {
  color: var(--bb-color-primary);
}
.admin__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 16px 20px 24px;
  overflow: auto;
}
</style>
