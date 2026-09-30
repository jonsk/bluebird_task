<script setup lang="ts">
import { computed, nextTick, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar,
  ChatDotRound,
  Delete,
  Edit,
  Fold,
  List,
  Notebook,
  Plus,
  Star,
  Sunny,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import BbCategoryTree from './BbCategoryTree.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTaskStore } from '@/stores/task'
import type { Menu } from '@/api/menu'

/**
 * BbFilterRail — 左栏（旧 `.left-box`）。逐项对齐旧系统：
 * ① 无顶部 header，用户块（姓名16/账号14）+ 折叠 Fold 在左栏顶部（`.left-title`，h64）；
 * ② 视图行 50px：18px 图标 + 14px 文案 + 右侧计数（`.tabs-item`）；
 * ③ 自定义栏与六视图**同一列表内联**（Notebook 图标，hover 显改/删）；
 * ④ 「新增自定义任务栏」行在列表末尾；⑤ 全部任务行带 2px `#0065c0` 底边线；
 * ⑥ 分类树（设计 §5.2 要求）置于旧 `.main-tabs-content` 位置（列表下方）。
 */
const app = useAppStore()
const auth = useAuthStore()
const taskStore = useTaskStore()
const menuStore = useMenuStore()
const route = useRoute()
const router = useRouter()

const navItems = [
  { name: 'index', label: '我的一天', countKey: 'day', icon: Sunny },
  { name: 'myWeek', label: '未来7天任务', countKey: 'week', icon: Calendar },
  { name: 'myJoin', label: '我@Ta的任务', countKey: 'joined', icon: ChatDotRound },
  { name: 'myDo', label: '分配给我的任务', countKey: 'assigned', icon: User },
  { name: 'myCollect', label: '我的收藏', countKey: 'collect', icon: Star },
  { name: 'allTask', label: '全部任务', countKey: 'all', icon: List },
] as const

const currentName = computed(() => String(route.name ?? ''))
const canManageUsers = computed(() => auth.hasRole('ADMIN', 'USER_MANAGER'))
const canViewAudit = computed(() => auth.hasRole('ADMIN', 'AUDITOR'))
const displayName = computed(() => auth.user?.name ?? auth.user?.username ?? '未登录')
const userName = computed(() => auth.user?.username ?? '')
const menus = computed(() => menuStore.menus)
const activeMenuId = computed(() => taskStore.menuId)

const creating = ref(false)
const editingId = ref<number | null>(null)
const draftName = ref('')
const nameInput = shallowRef<{ focus: () => void } | null>(null)

function countOf(key: string): number | null {
  const c = taskStore.counts
  if (!c) return null
  return Number((c as unknown as Record<string, number>)[key] ?? 0)
}

function goView(name: string): void {
  taskStore.setMenuFilter(null)
  taskStore.setCategoryFilter(null)
  if (route.name !== name) void router.push({ name })
}

function selectMenu(menu: Menu): void {
  if (menu.id == null) return
  if (editingId.value != null) return
  taskStore.setMenuFilter(Number(menu.id))
  if (route.name !== 'allTask') void router.push({ name: 'allTask' })
}

function focusDraft(): void {
  void nextTick(() => nameInput.value?.focus())
}

function startCreate(): void {
  if (creating.value || editingId.value != null) return
  creating.value = true
  draftName.value = ''
  focusDraft()
}

async function commitCreate(): Promise<void> {
  if (!creating.value) return
  const name = draftName.value.trim()
  creating.value = false
  if (!name) return
  try {
    await menuStore.create(name)
    ElMessage.success('已新建自定义栏')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '新建失败')
  }
}

function startEdit(menu: Menu): void {
  if (creating.value || editingId.value != null) return
  editingId.value = Number(menu.id)
  draftName.value = menu.name ?? ''
  focusDraft()
}

async function commitEdit(menu: Menu): Promise<void> {
  if (editingId.value == null) return
  const id = editingId.value
  const name = draftName.value.trim()
  editingId.value = null
  if (!name || name === menu.name) return
  try {
    await menuStore.rename(id, name)
    ElMessage.success('已重命名')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '重命名失败')
  }
}

async function onDelete(menu: Menu): Promise<void> {
  const count = menu.items?.length ?? 0
  const tip = count
    ? `「${menu.name}」含 ${count} 个任务，删除仅移除该栏（不影响任务）。确认删除？`
    : `确认删除自定义栏「${menu.name}」？`
  try {
    await ElMessageBox.confirm(tip, '删除自定义栏', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await menuStore.remove(Number(menu.id))
    if (taskStore.menuId === Number(menu.id)) taskStore.setMenuFilter(null)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '删除失败')
  }
}

async function runHandler(command: string): Promise<void> {
  if (command !== 'logout') return
  await auth.logout()
  await router.replace('/login')
}

function onSelectCategory(id: number | null): void {
  taskStore.setCategoryFilter(id)
}
</script>

<template>
  <aside class="left-box">
    <div class="left-title">
      <el-dropdown class="left-title__user" trigger="click" @command="runHandler">
        <div class="left-title__inner">
          <p class="left-title__name">{{ displayName }}</p>
          <p class="left-title__account">
            <span>{{ userName }}</span>
            <el-icon><SwitchButton /></el-icon>
          </p>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="left-title__menu">
            <el-dropdown-item command="logout">
              <div class="left-title__logout">
                <el-icon class="t-b-i-b-c-l-d-item-icon"><SwitchButton /></el-icon>
                <span class="ml-5">退出登录</span>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-tooltip content="收起菜单" placement="bottom" effect="light">
        <el-icon class="left-title__fold" data-test="rail-fold" @click="app.toggleLeft()"><Fold /></el-icon>
      </el-tooltip>
    </div>

    <div class="left-box__scroll">
      <div class="main-tabs-box">
        <ul class="main-tabs-left-top">
          <li v-for="item in navItems" :key="item.name">
            <div
              class="tabs-item"
              :data-test="`nav-${item.name}`"
              :style="item.name === 'allTask' ? { borderBottom: '2px solid #0065c0' } : undefined"
              :class="{ 'item-active': currentName === item.name }"
              @click="goView(item.name)"
            >
              <div class="tabs-item-left">
                <el-icon class="t-i-l-icon"><component :is="item.icon" /></el-icon>
                <span class="t-i-l-text">{{ item.label }}</span>
              </div>
              <div class="tabs-item-right">
                <span v-if="countOf(item.countKey) !== null" class="layout__count">{{ countOf(item.countKey) }}</span>
              </div>
            </div>
          </li>

          <li v-for="m in menus" :key="`menu-${m.id}`">
            <div
              class="tabs-item bb-menu-row"
              data-test="menu-row"
              :class="{ 'item-active': activeMenuId === Number(m.id) }"
              @click="selectMenu(m)"
            >
              <div class="tabs-item-left">
                <el-icon class="t-i-l-icon"><Notebook /></el-icon>
                <template v-if="editingId === Number(m.id)">
                  <el-input
                    ref="nameInput"
                    v-model="draftName"
                    size="small"
                    class="bb-menu-row__input"
                    @click.stop
                    @keyup.enter="commitEdit(m)"
                    @blur="commitEdit(m)"
                  />
                </template>
                <span v-else class="t-i-l-text">{{ m.name }}</span>
              </div>
              <div class="tabs-item-right">
                <span class="bb-menu-row__ops">
                  <el-icon class="bb-menu-row__op" title="重命名" @click.stop="startEdit(m)"><Edit /></el-icon>
                  <el-icon class="bb-menu-row__op" title="删除" @click.stop="onDelete(m)"><Delete /></el-icon>
                </span>
              </div>
            </div>
          </li>

          <li v-if="creating">
            <div class="tabs-item bb-menu-row">
              <div class="tabs-item-left">
                <el-icon class="t-i-l-icon"><Notebook /></el-icon>
                <el-input
                  ref="nameInput"
                  v-model="draftName"
                  size="small"
                  class="bb-menu-row__input"
                  placeholder="栏名称，回车保存"
                  @keyup.enter="commitCreate"
                  @blur="commitCreate"
                />
              </div>
            </div>
          </li>

          <div class="tabs-item bb-add-row" data-test="menu-add" @click="startCreate">
            <div class="tabs-item-left">新增自定义任务栏</div>
            <div class="tabs-item-right">
              <el-icon class="t-i-l-icon"><Plus /></el-icon>
            </div>
          </div>
        </ul>
      </div>

      <div class="main-tabs-content">
        <BbCategoryTree @select="onSelectCategory" />
      </div>
    </div>

    <div class="left-box__bottom">
      <el-icon class="left-box__bottom-icon" title="日历" data-test="nav-calendar" @click="goView('calendar')"><Calendar /></el-icon>
      <el-icon v-if="canManageUsers" class="left-box__bottom-icon" title="组织管理" data-test="nav-adminOrg" @click="goView('adminOrg')"><User /></el-icon>
      <el-icon v-if="canViewAudit" class="left-box__bottom-icon" title="操作/登录日志" data-test="nav-adminAudit" @click="goView('adminAudit')"><List /></el-icon>
    </div>
  </aside>
</template>

<style scoped>
.left-box {
  /* flex 项默认 min-width:auto 会被内部树内容撑宽 → 固定 300px */
  position: relative;
  display: flex;
  flex: 0 0 var(--bb-sidebar-left-width);
  flex-direction: column;
  width: var(--bb-sidebar-left-width);
  min-width: 0;
  height: 100vh;
  box-sizing: border-box;
  background: var(--bg-primary);
  box-shadow: var(--bb-shadow-flat);
  z-index: 2;
}
.left-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--bb-title-height);
  padding: 3px 10px;
  box-sizing: border-box;
  user-select: none;
  box-shadow: 0 0.5px 0 0 var(--bg-separator);
}
.left-title__user {
  width: calc(100% - 50px);
  color: var(--bb-text-secondary);
}
.left-title__inner {
  min-height: 50px;
  width: 100%;
  padding: 10px;
  box-sizing: border-box;
  overflow: hidden;
  cursor: pointer;
}
.left-title__name {
  margin: 0;
  font-size: 16px;
  line-height: 1.2;
  color: var(--bb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.left-title__account {
  display: flex;
  align-items: center;
  margin: 2px 0 0;
  font-size: 14px;
  line-height: 1.2;
  color: var(--bb-text-muted);
  overflow: hidden;
}
.left-title__account .el-icon {
  margin-left: 10px;
  font-size: 14px;
}
.left-title__logout {
  display: flex;
  align-items: center;
  padding: 8px 0;
}
.ml-5 {
  margin-left: 5px;
}
.left-title__fold {
  margin-right: 5px;
  font-size: 18px;
  cursor: pointer;
}
.left-box__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.main-tabs-left-top {
  margin: 0;
  padding: 0;
  list-style: none;
}
.tabs-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--bb-row-height);
  padding: 0 10px 0 20px;
  box-sizing: border-box;
  font-size: 14px;
  line-height: var(--bb-row-height);
  cursor: pointer;
}
.tabs-item:hover {
  background: var(--bg-hover);
}
.tabs-item.item-active {
  background: var(--bg-active);
  font-weight: 700;
}
.tabs-item-left {
  display: flex;
  align-items: center;
  min-width: 0;
}
.t-i-l-icon {
  margin-top: -2px;
  font-size: 18px;
}
.t-i-l-text {
  margin-left: 10px;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tabs-item-right {
  display: flex;
  flex: 1 1 0%;
  align-items: center;
  justify-content: flex-end;
  padding-right: 10px;
  font-size: 14px;
}
.bb-menu-row__ops {
  display: none;
  align-items: center;
  gap: 8px;
}
.bb-menu-row:hover .bb-menu-row__ops {
  display: inline-flex;
}
.bb-menu-row__op {
  color: var(--bb-text-muted);
}
.bb-menu-row__op:hover {
  color: var(--bg-accent);
}
.bb-menu-row__input {
  margin-left: 10px;
  width: 150px;
}
.bb-add-row {
  align-items: center;
  padding: 0 7px 0 20px;
  font-size: 14px;
  cursor: pointer;
}
.main-tabs-content {
  padding: 0 20px 12px;
}
.left-box__bottom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  width: 100%;
  height: 50px;
  flex: 0 0 50px;
  box-shadow: 0 -0.5px 0 0 var(--bg-separator);
}
.left-box__bottom-icon {
  font-size: 18px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.left-box__bottom-icon:hover {
  color: var(--bg-accent);
}
</style>
