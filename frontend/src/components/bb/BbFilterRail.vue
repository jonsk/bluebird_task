<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  Brush,
  Calendar,
  ChatDotRound,
  Delete,
  Edit,
  Fold,
  Folder,
  Key,
  List,
  Notebook,
  OfficeBuilding,
  Plus,
  Search,
  Setting,
  Star,
  Sunny,
  SwitchButton,
  User,
  View,
} from '@element-plus/icons-vue'
import { useAppStore, type ThemeKey } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTaskStore } from '@/stores/task'
import type { Menu } from '@/api/menu'
import { logoutEverywhere } from '@/utils/logout'

/**
 * BbFilterRail — 左栏（旧 `.left-box`）。逐项对齐旧系统：
 * ① 无顶部 header，用户块（姓名16/账号14）+ 折叠 Fold 在左栏顶部（`.left-title`，h64）；
 * ② 视图行 50px：18px 图标 + 14px 文案 + 右侧计数（`.tabs-item`）；
 * ③ 自定义栏与各视图**同一列表内联**（Notebook 图标，hover 显改/删）；
 * ④ 「新增自定义任务栏」行在列表末尾；⑤ 全部任务行带 2px 底边线；
 * ⑥ 分类树已移除（2026-10-13，左栏下方不再有分类区块）。
 * ⑦ 2026-10-24 方案 A：按 `route.meta.module` 分「任务 / 工作日志」两组，
 *    分组折叠状态按模块持久化（`bb.filter-rail.<module>.collapsed`），
 *    工作日志组含「探索」，AUDITOR 与普通用户一致显示；task 组在 note 页隐藏自定义栏。
 */
const app = useAppStore()
const auth = useAuthStore()
const taskStore = useTaskStore()
const menuStore = useMenuStore()
const route = useRoute()
const router = useRouter()

const taskNavItems = [
  { name: 'index', label: '我的一天', countKey: 'day', icon: Sunny },
  { name: 'myJoin', label: '我@Ta的任务', countKey: 'joined', icon: ChatDotRound },
  { name: 'myDo', label: '分配给我的任务', countKey: 'assigned', icon: User },
  { name: 'myCollect', label: '我的收藏', countKey: 'collect', icon: Star },
  { name: 'allTask', label: '全部任务', countKey: 'all', icon: List },
] as const

/** 当前模块（路由 meta.module 单一来源；无 meta → 任务模块）。 */
const module = computed<'task' | 'note'>(() => (route.meta.module === 'note' ? 'note' : 'task'))

/** 工作日志导航：下属入口看 `managerCanReadSubordinate`，全部/全部归档仅 ADMIN。 */
const noteNavItems = computed<
  Array<{ name: string; label: string; icon: unknown; test: string }>
>(() => {
  const items: Array<{ name: string; label: string; icon: unknown; test: string }> = [
    { name: 'NoteHome', label: '我的日志', icon: Notebook, test: 'note-mine' },
    { name: 'NoteExplore', label: '探索', icon: Search, test: 'note-explore' },
    { name: 'NoteShared', label: '被授权日志', icon: View, test: 'note-shared' },
    { name: 'NoteInternal', label: '本部门日志', icon: OfficeBuilding, test: 'note-internal' },
  ]
  if (auth.user?.managerCanReadSubordinate) {
    items.push({ name: 'NoteSubordinate', label: '下属日志', icon: User, test: 'note-subordinate' })
  }
  if (auth.hasRole('ADMIN')) {
    items.push({ name: 'NoteAll', label: '全部日志', icon: List, test: 'note-all' })
  }
  items.push(
    { name: 'NoteArchived', label: '归档日志', icon: Folder, test: 'note-archived' },
    { name: 'NoteGrants', label: '授权管理', icon: Key, test: 'note-grants' },
  )
  return items
})

/** 分组折叠：分组级持久化，首访按"当前模块展开、另一模块收起"。 */
const COLLAPSE_KEY = (m: 'task' | 'note') => `bb.filter-rail.${m}.collapsed`
const collapsed = ref<Record<'task' | 'note', boolean>>({ task: false, note: true })
function restoreCollapse(): void {
  try {
    const stored = localStorage.getItem(COLLAPSE_KEY(module.value))
    collapsed.value = {
      task: module.value === 'task' ? stored !== '1' : localStorage.getItem(COLLAPSE_KEY('task')) === '1',
      note: module.value === 'note' ? stored !== '1' : localStorage.getItem(COLLAPSE_KEY('note')) === '1',
    }
  } catch {
    collapsed.value = { task: module.value === 'task', note: module.value === 'note' }
  }
}
function toggleGroup(m: 'task' | 'note'): void {
  collapsed.value[m] = !collapsed.value[m]
  try {
    localStorage.setItem(COLLAPSE_KEY(m), collapsed.value[m] ? '1' : '0')
  } catch {
    /* localStorage 不可用时仅内存态 */
  }
}
restoreCollapse()
watch(
  module,
  (m) => {
    // 切模块时**必定**展开当前分组：导航项必须随路由可见，否则进入某模块却看不到任何入口。
    // 用户在同一模块内的手动收起仍会持久化（module 不变 → 不触发本 watch）。
    collapsed.value[m] = false
  },
  { immediate: true },
)

const currentName = computed(() => String(route.name ?? ''))
/** 后台入口（独立布局）：ADMIN/AUDITOR/USER_MANAGER 三者任一即可进入，页面级再按角色收窄。 */
const canEnterAdmin = computed(() => auth.hasRole('ADMIN', 'AUDITOR', 'USER_MANAGER'))
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
  if (route.name !== name) void router.push({ name })
}

/** 底部日历图标：按当前模块跳到对应日历页（任务 → /calendar；工作日志 → /notes/calendar）。 */
function goCalendar(): void {
  if (module.value === 'note') {
    if (route.name !== 'NoteCalendar') void router.push({ name: 'NoteCalendar' })
    return
  }
  goView('calendar')
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
  await logoutEverywhere(router)
}

/** 内置主题清单（key 与 tokens.css data-theme 作用域一致，展示用色块）。 */
const themeList: ReadonlyArray<{ key: ThemeKey; label: string; color: string }> = [
  { key: 'light-classic', label: '经典浅色', color: '#0065c0' },
  { key: 'light-modern', label: '现代浅色', color: '#1677ff' },
  { key: 'teal', label: '青绿', color: '#0ba5a0' },
  { key: 'dark', label: '深色', color: '#3b82f6' },
]

function onThemeCommand(command: string | number | object): void {
  app.setTheme(command as ThemeKey)
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
        <div class="nav-group" data-test="nav-group-task">
          <div
            class="nav-group__head"
            :class="{ 'is-active': module === 'task' }"
            data-test="nav-group-task-head"
            @click="toggleGroup('task')"
          >
            <span class="nav-group__title">任务</span>
            <el-icon class="nav-group__caret">
              <component :is="collapsed.task ? ArrowRight : ArrowDown" />
            </el-icon>
          </div>

          <ul v-show="!collapsed.task" class="main-tabs-left-top">
            <li v-for="item in taskNavItems" :key="item.name">
              <div
                class="tabs-item"
                :data-test="`nav-${item.name}`"
                :style="item.name === 'allTask' ? { borderBottom: '2px solid var(--bg-accent)' } : undefined"
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

            <template v-if="module === 'task'">
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
            </template>
          </ul>
        </div>

        <div class="nav-group" data-test="nav-group-note">
          <div
            class="nav-group__head"
            :class="{ 'is-active': module === 'note' }"
            data-test="nav-group-note-head"
            @click="toggleGroup('note')"
          >
            <span class="nav-group__title">工作日志</span>
            <el-icon class="nav-group__caret">
              <component :is="collapsed.note ? ArrowRight : ArrowDown" />
            </el-icon>
          </div>

          <ul v-show="!collapsed.note" class="main-tabs-left-top">
            <li v-for="item in noteNavItems" :key="item.name">
              <div
                class="tabs-item"
                :data-test="`nav-${item.test}`"
                :class="{ 'item-active': currentName === item.name }"
                @click="goView(item.name)"
              >
                <div class="tabs-item-left">
                  <el-icon class="t-i-l-icon"><component :is="item.icon" /></el-icon>
                  <span class="t-i-l-text">{{ item.label }}</span>
                </div>
                <div class="tabs-item-right"></div>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <div class="left-box__bottom">
      <el-tooltip content="日历" placement="top" effect="light">
        <el-icon class="left-box__bottom-icon" title="日历" data-test="nav-calendar" @click="goCalendar"><Calendar /></el-icon>
      </el-tooltip>
      <el-tooltip v-if="canEnterAdmin" content="后台管理" placement="top" effect="light">
        <el-icon class="left-box__bottom-icon" title="后台管理" data-test="nav-admin" @click="router.push('/admin')"><Setting /></el-icon>
      </el-tooltip>
      <el-tooltip content="主题" placement="top" effect="light">
        <el-dropdown trigger="click" @command="onThemeCommand">
          <el-icon class="left-box__bottom-icon" title="主题" data-test="nav-theme"><Brush /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in themeList"
                :key="t.key"
                :command="t.key"
                :class="{ 'theme-item-active': app.theme === t.key }"
              >
                <span class="theme-swatch" :style="{ background: t.color }"></span>
                <span>{{ t.label }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-tooltip>
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
.nav-group + .nav-group {
  margin-top: 6px;
  border-top: 1px solid var(--bg-separator);
  padding-top: 6px;
}
.nav-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 12px 0 20px;
  box-sizing: border-box;
  font-size: 12px;
  color: var(--bb-text-muted);
  cursor: pointer;
  user-select: none;
}
.nav-group__head:hover {
  background: var(--bg-hover);
}
.nav-group__head.is-active .nav-group__title {
  color: var(--bb-text-secondary);
  font-weight: 700;
}
.nav-group__caret {
  font-size: 12px;
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
.theme-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 8px;
  border-radius: 3px;
  vertical-align: -1px;
  border: 1px solid var(--bg-separator);
}
.theme-item-active {
  font-weight: 700;
  color: var(--bb-color-primary);
}
</style>
