import type { RouteRecordRaw } from 'vue-router'
import type { RoleCode } from '@/utils/constants'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    roles?: RoleCode[]
    /** 左栏分组单一来源：'task' | 'note'（缺省按任务处理） */
    module?: 'task' | 'note'
    /** note 列表 scope（7 值集合的子集；NoteListView/useNoteList 唯一输入） */
    scope?: 'mine' | 'explore' | 'shared' | 'internal' | 'subordinate' | 'visible' | 'all'
    /** note 归档分段：true = archived 查询 */
    archived?: boolean
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
      /**
       * 工作日志（note）。**声明顺序是硬约束**：静态段必须排在 `notes/:id(\d+)` 之前，
       * 否则 `/notes/calendar` 会被 `:id` 吞（`\d+` 只能挡住字母段，不能作为依赖）。
       */
      {
        path: 'notes',
        name: 'NoteHome',
        component: () => import('@/views/note/NoteHomeView.vue'),
        meta: { module: 'note', scope: 'mine', title: '我的日志', requiresAuth: true },
      },
      {
        path: 'notes/explore',
        name: 'NoteExplore',
        component: () => import('@/views/note/NoteExploreView.vue'),
        meta: { module: 'note', scope: 'explore', title: '探索', requiresAuth: true },
      },
      {
        path: 'notes/shared',
        name: 'NoteShared',
        component: () => import('@/views/note/NoteSharedView.vue'),
        meta: { module: 'note', scope: 'shared', title: '被授权日志', requiresAuth: true },
      },
      {
        path: 'notes/internal',
        name: 'NoteInternal',
        component: () => import('@/views/note/NoteInternalView.vue'),
        meta: { module: 'note', scope: 'internal', title: '本部门日志', requiresAuth: true },
      },
      {
        path: 'notes/subordinate',
        name: 'NoteSubordinate',
        component: () => import('@/views/note/NoteSubordinateView.vue'),
        meta: { module: 'note', scope: 'subordinate', title: '下属日志', requiresAuth: true },
      },
      {
        path: 'notes/all',
        name: 'NoteAll',
        component: () => import('@/views/note/NoteAllView.vue'),
        meta: { module: 'note', scope: 'all', title: '全部日志', roles: ['ADMIN'], requiresAuth: true },
      },
      {
        path: 'notes/grants',
        name: 'NoteGrants',
        component: () => import('@/views/note/NoteUserGrantView.vue'),
        meta: { module: 'note', title: '授权管理', requiresAuth: true },
      },
      {
        path: 'notes/calendar',
        name: 'NoteCalendar',
        component: () => import('@/views/note/NoteCalendarView.vue'),
        meta: { module: 'note', title: '日志日历', requiresAuth: true },
      },
      {
        path: 'notes/archived',
        name: 'NoteArchived',
        component: () => import('@/views/note/NoteArchivedView.vue'),
        meta: { module: 'note', scope: 'mine', archived: true, title: '归档日志', requiresAuth: true },
      },
      {
        path: 'notes/archived/all',
        name: 'NoteArchivedAll',
        component: () => import('@/views/note/NoteArchivedView.vue'),
        meta: { module: 'note', scope: 'all', archived: true, title: '全部归档', roles: ['ADMIN'], requiresAuth: true },
      },
      {
        path: 'notes/:id(\\d+)',
        name: 'NoteDetail',
        component: () => import('@/views/note/NoteDetailView.vue'),
        meta: { module: 'note', title: '日志详情', requiresAuth: true },
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
