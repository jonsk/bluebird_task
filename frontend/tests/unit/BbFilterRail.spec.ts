import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

/**
 * BbFilterRail（方案 A：任务/工作日志两组）：
 * 1) 日志组按 权限 动态追加「下属日志 / 全部日志」，并始终含归档与授权管理
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
  it('普通用户：无「下属日志 / 全部日志」，含归档与授权管理', () => {
    const w = mountRail()
    const tests = navTests(w)
    expect(tests).toContain('nav-note-mine')
    expect(tests).toContain('nav-note-explore')
    expect(tests).toContain('nav-note-shared')
    expect(tests).toContain('nav-note-internal')
    expect(tests).not.toContain('nav-note-subordinate')
    expect(tests).not.toContain('nav-note-all')
    expect(tests).toContain('nav-note-archived')
    expect(tests).toContain('nav-note-grants')
  })

  it('managerCanReadSubordinate=true 时出现「下属日志」', () => {
    authState.user = { name: '张三', username: 'zhangli', managerCanReadSubordinate: true }
    const w = mountRail()
    expect(navTests(w)).toContain('nav-note-subordinate')
  })

  it('ADMIN 出现「全部日志」', () => {
    authState.hasRole = (...roles: string[]) => roles.includes('ADMIN')
    const w = mountRail()
    expect(navTests(w)).toContain('nav-note-all')
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
    route.name = 'NoteExplore'
    const w = mountRail()
    const active = w.find('[data-test="nav-note-explore"]')
    expect(active.classes()).toContain('item-active')
    await w.find('[data-test="nav-note-shared"]').trigger('click')
    expect(router.push).toHaveBeenCalled()
  })
})
