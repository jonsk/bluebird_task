import type { RouteRecordRaw } from 'vue-router'
import type { RoleCode } from '@/utils/constants'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    roles?: RoleCode[]
  }
}

const DefaultLayout = () => import('@/layouts/DefaultLayout.vue')
const AdminLayout = () => import('@/layouts/AdminLayout.vue')
const TaskView = () => import('@/views/task/TaskView.vue')

/** 声明式路由（03 §4.3）。五大任务视图统一 TaskView，按 route.name 经 ROUTE_SCOPE_MAP 取 scope。 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/oauth/success',
    name: 'oidcSuccess',
    component: () => import('@/views/auth/OidcSuccessView.vue'),
    meta: { title: '登录中', requiresAuth: false },
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/index',
    meta: { requiresAuth: true },
    children: [
      { path: 'index', name: 'index', component: TaskView, meta: { title: '我的一天' } },
      { path: 'myJoin', name: 'myJoin', component: TaskView, meta: { title: '我@Ta的任务' } },
      { path: 'myDo', name: 'myDo', component: TaskView, meta: { title: '分配给我的任务' } },
      { path: 'myCollect', name: 'myCollect', component: TaskView, meta: { title: '我的收藏' } },
      { path: 'allTask', name: 'allTask', component: TaskView, meta: { title: '全部任务' } },
      {
        path: 'calendar',
        name: 'calendar',
        component: () => import('@/views/task/CalendarView.vue'),
        meta: { title: '日历' },
      },
    ],
  },
  /**
   * 后台（独立布局，2026-10-18 用户拍板）：不带任务左栏，左侧固定导航。
   * 可见范围按角色收窄：概览=三者皆可，用户与机构=ADMIN/USER_MANAGER，
   * Casdoor 与常规设置=仅 ADMIN，日志=ADMIN/AUDITOR。
   */
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN', 'AUDITOR', 'USER_MANAGER'] },
    children: [
      {
        path: '',
        name: 'adminOverview',
        component: () => import('@/views/admin/AdminOverview.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'users',
        name: 'adminUsers',
        component: () => import('@/views/admin/OrgManage.vue'),
        meta: { title: '用户与机构', roles: ['ADMIN', 'USER_MANAGER'] },
      },
      {
        path: 'casdoor',
        name: 'adminCasdoor',
        component: () => import('@/views/admin/CasdoorConfig.vue'),
        meta: { title: 'Casdoor 对接', roles: ['ADMIN'] },
      },
      {
        path: 'logs',
        name: 'adminLogs',
        component: () => import('@/views/admin/AdminLogs.vue'),
        meta: { title: '日志', roles: ['ADMIN', 'AUDITOR'] },
      },
      {
        path: 'settings',
        name: 'adminSettings',
        component: () => import('@/views/admin/AdminSettings.vue'),
        meta: { title: '常规设置', roles: ['ADMIN'] },
      },
    ],
  },
  /* 旧路径兼容：/admin/org → /admin/users，/admin/audit → /admin/logs */
  { path: '/admin/org', redirect: '/admin/users' },
  { path: '/admin/audit', redirect: '/admin/logs' },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权限', requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在', requiresAuth: false },
  },
]
