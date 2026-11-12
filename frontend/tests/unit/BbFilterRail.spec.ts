import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

/**
 * BbFilterRail（方案 A：任务/工作日志两组）：
 * 1) 日志组只留普通入口（我的 / 被授权 / 员工(开关) / 授权管理），不含「全部日志」（已在后台 /admin/notes）
 * 2) 分组折叠按模块持久化到 localStorage，切模块时当前组必定展开
 * 3) 当前路由高亮 is-active
 */

const route = reactive({
  name: 'NoteHome',
  path: '/notes',
  query: {} as Record<string, string>,
  meta: { module: 'note' } as Record<string, unknown>,
})
const router = { push: vi.fn(() => Promise.resolve()), replace: vi.fn(() => Promise.resolve()) }
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

const appStore = reactive({ leftCollapsed: false, toggleLeft: vi.fn(), theme: 'light', setTheme: vi.fn() })
vi.mock('@/stores/app', () => ({ useAppStore: () => appStore }))

const authState = reactive({
  user: { name: '张三', username: 'zhangli', managerCanReadSubordinate: false } as Record<string, unknown> | null,
  hasRole: (...roles: string[]) => roles.includes('USER'),
  isAuthenticated: true,
})
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

const menuState = reactive({ menus: [] as unknown[], loaded: true, load: vi.fn() })
vi.mock('@/stores/menu', () => ({ useMenuStore: () => menuState }))

const taskState = reactive({
  menuId: null as number | null,
  counts: {} as Record<string, number>,
  setMenuId: vi.fn(),
  setMenuFilter: vi.fn(),
})
vi.mock('@/stores/task', () => ({ useTaskStore: () => taskState }))

vi.mock('@/utils/logout', () => ({ logoutEverywhere: vi.fn() }))

vi.mock('element-plus', () => {
  const confirm = vi.fn()
  return {
    ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: Object.assign(confirm, { confirm }),
  }
})

vi.mock('@element-plus/icons-vue', () => {
  const icon = { template: '<i />' }
  const names = [
    'ArrowDown',
    'ArrowRight',
    'Brush',
    'Calendar',
    'ChatDotRound',
    'Delete',
    'Document',
    'Edit',
    'Fold',
    'Folder',
    'Key',
    'List',
    'Notebook',
    'OfficeBuilding',
    'Plus',
    'Search',
    'Setting',
    'Star',
    'Sunny',
    'SwitchButton',
    'User',
    'View',
  ]
  return Object.fromEntries(names.map((n) => [n, icon]))
})

import BbFilterRail from '@/components/bb/BbFilterRail.vue'
import { logoutEverywhere } from '@/utils/logout'

const stubs = {
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-tooltip': { template: '<span><slot /></span>' },
  'el-scrollbar': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-dropdown': { template: '<div><slot /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
}

function mountRail() {
  return mount(BbFilterRail, { global: { stubs } })
}

const navTests = (w: ReturnType<typeof mountRail>): string[] =>
  w.findAll('[data-test^="nav-note-"]').map((n) => n.attributes('data-test') ?? '')

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
  route.name = 'NoteHome'
  route.path = '/notes'
  route.query = {}
  route.meta = { module: 'note' }
  authState.user = { name: '张三', username: 'zhangli', managerCanReadSubordinate: false }
  authState.hasRole = (...roles: string[]) => roles.includes('USER')
  router.push.mockClear()
})

describe('BbFilterRail · 工作日志分组', () => {
  it('普通用户：只有我的日志，无员工、无全部日志、无授权入口', () => {
    const w = mountRail()
    const tests = navTests(w)
    expect(tests).toEqual(['nav-note-mine', 'nav-note-weekly', 'nav-note-weekly-report'])
    expect(tests).not.toContain('nav-note-shared')
    expect(tests).not.toContain('nav-note-grants')
  })

  it('managerCanReadSubordinate=true 时出现「下属日志」', () => {
    authState.user = { name: '张三', username: 'zhangli', managerCanReadSubordinate: true }
    const w = mountRail()
    expect(navTests(w)).toContain('nav-note-subordinate')
    // 周记录同口径：可读下属日志才有「员工周记录」
    expect(navTests(w)).toContain('nav-note-weekly-subordinate')
  })

  it('ADMIN 也不出现「全部日志」（已移到后台 /admin/notes）', () => {
    authState.hasRole = (...roles: string[]) => roles.includes('ADMIN')
    const w = mountRail()
    expect(navTests(w)).not.toContain('nav-note-all')
  })

  it('日志页也渲染自定义栏和新增自定义任务栏入口', () => {
    const w = mountRail()
    expect(w.find('[data-test="menu-add"]').exists()).toBe(true)
  })

  it('两组分组都存在，且日志页时日志组展开', () => {
    const w = mountRail()
    expect(w.find('[data-test="nav-group-task"]').exists()).toBe(true)
    expect(w.find('[data-test="nav-group-note"]').exists()).toBe(true)
    const noteList = w.find('[data-test="nav-group-note"] ul')
    expect(noteList.attributes('style') ?? '').not.toContain('display: none')
  })

  it('点击分组头切换折叠并写入 localStorage；切模块后当前组强制展开', async () => {
    const w = mountRail()
    await w.find('[data-test="nav-group-note-head"]').trigger('click')
    expect(localStorage.getItem('bb.filter-rail.note.collapsed')).toBe('1')

    await w.find('[data-test="nav-group-note-head"]').trigger('click')
    expect(localStorage.getItem('bb.filter-rail.note.collapsed')).toBe('0')

    // 同一路由内手动收起 → 切到 task 再回 note，note 组必须再次展开
    await w.find('[data-test="nav-group-note-head"]').trigger('click')
    route.meta = { module: 'task' }
    await w.vm.$nextTick()
    route.meta = { module: 'note' }
    await w.vm.$nextTick()
    const ul = w.find('[data-test="nav-group-note"] ul')
    expect(ul.attributes('style') ?? '').not.toContain('display: none')
  })

  it('当前路由项高亮 is-active，点击项触发 router.push', async () => {
    authState.user = { name: '张三', username: 'zhangli', managerCanReadSubordinate: true }
    route.name = 'NoteSubordinate'
    const w = mountRail()
    const active = w.find('[data-test="nav-note-subordinate"]')
    expect(active.classes()).toContain('item-active')
    await w.find('[data-test="nav-note-mine"]').trigger('click')
    expect(router.push).toHaveBeenCalled()
  })
})

describe('BbFilterRail · 左上角用户区', () => {
  it('只显示一个名字（姓名优先，没有姓名时回落到账号）', () => {
    const w = mountRail()
    const name = w.find('.left-title__name')
    expect(name.text()).toBe('张三')
    // 账号不再单独占一行
    expect(w.text()).not.toContain('zhangli')

    authState.user = { name: '', username: 'zhangli' }
    const w2 = mountRail()
    expect(w2.find('.left-title__name').text()).toBe('zhangli')
  })

  it('点退出图标直接退出，不弹二级下拉菜单', async () => {
    vi.mocked(logoutEverywhere).mockClear()
    const w = mountRail()
    expect(w.findComponent({ name: 'ElDropdown' }).exists()).toBe(false)
    await w.find('[data-test="rail-logout"]').trigger('click')
    expect(logoutEverywhere).toHaveBeenCalledTimes(1)
  })
})
