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
const TaskView = () => import('@/views/task/TaskView.vue')

/** 声明式路由（03 §4.3）。六大视图统一 TaskView，按 route.name 经 ROUTE_SCOPE_MAP 取 scope。 */
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
      { path: 'myWeek', name: 'myWeek', component: TaskView, meta: { title: '未来 7 天' } },
      { path: 'myJoin', name: 'myJoin', component: TaskView, meta: { title: '我@Ta的' } },
      { path: 'myDo', name: 'myDo', component: TaskView, meta: { title: '分配给我的' } },
      { path: 'myCollect', name: 'myCollect', component: TaskView, meta: { title: '我的收藏' } },
      { path: 'allTask', name: 'allTask', component: TaskView, meta: { title: '全部任务' } },
      {
        path: 'admin/users',
        name: 'adminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', roles: ['ADMIN', 'USER_MANAGER'] },
      },
      {
        path: 'admin/audit',
        name: 'adminAudit',
        component: () => import('@/views/admin/AuditLog.vue'),
        meta: { title: '操作/登录日志', roles: ['ADMIN', 'AUDITOR'] },
      },
    ],
  },
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
