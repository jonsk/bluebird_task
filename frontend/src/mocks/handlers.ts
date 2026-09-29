import { http, HttpResponse } from 'msw'

/**
 * MSW 契约 Mock（03 §2.6）。仅 dev/test 装载；端点按 openapi.yaml 对齐。
 * 内存态数据，重启即重置；用于前后端并行开发。
 */

const AUTH = '/api/v1'

function ok<T>(data: T): Response {
  return HttpResponse.json({ code: 0, message: 'ok', data, traceId: 'mock-trace' })
}

function fail(code: number, message: string): Response {
  return HttpResponse.json({ code, message, data: null, traceId: 'mock-trace' })
}

const MOCK_USER = {
  id: 1,
  name: '管理员',
  username: 'admin',
  roleCode: 'ADMIN',
  deptId: 1,
  deptName: '总部',
  mobile: '138****0000',
  avatarUrl: null,
}

const USERS = [
  MOCK_USER,
  { id: 2, name: '张三', username: 'zhangsan', roleCode: 'COMMON', deptId: 2, deptName: '研发部', mobile: '139****0001', avatarUrl: null },
  { id: 3, name: '李四', username: 'lisi', roleCode: 'USER_MANAGER', deptId: 2, deptName: '研发部', mobile: '139****0002', avatarUrl: null },
]

const DEPARTMENTS = [
  { id: 1, name: '总部', parentId: null, leaderId: 1, sort: 0, children: [
    { id: 2, name: '研发部', parentId: 1, leaderId: 3, sort: 0, children: [] },
  ] },
]

const CATEGORIES = [
  { id: 1, name: '工作', parentId: null, scope: 'PERSONAL', deptId: null, ownerId: 1, sort: 0, taskCount: 1, children: [] },
  { id: 2, name: '汇报', parentId: 1, scope: 'PERSONAL', deptId: null, ownerId: 1, sort: 0, taskCount: 1, children: [] },
]

const TAGS = [
  { id: 1, name: '重要' },
  { id: 2, name: '跟进' },
]

interface MockTask {
  id: number
  title: string
  content: string
  status: string
  completed: boolean
  priority: string
  dueAt: string | null
  remindAt: string | null
  cycleRule: unknown
  cycleLastCompleted: string | null
  owner: { id: number; name: string }
  assignees: { id: number; name: string }[]
  ccUsers: { id: number; name: string }[]
  participantIds: number[]
  version: number
  category: { id: number; name: string } | null
  tags: { id: number; name: string }[]
  subtaskCompleted: number
  subtaskTotal: number
  files: { id: number; fileName: string; size: number }[]
  createdAt: string
}

let seq = 1000
const collected = new Set<number>()

function iso(offsetDays: number, hour = 18): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  d.setHours(hour, 0, 0, 0)
  return d.toISOString()
}

const TASKS: MockTask[] = [
  {
    id: 1,
    title: '提交季度报告',
    content: '整理数据并评审',
    status: 'ACTIVE',
    completed: false,
    priority: 'HIGH',
    dueAt: iso(0),
    remindAt: null,
    cycleRule: null,
    cycleLastCompleted: null,
    owner: { id: 1, name: '管理员' },
    assignees: [{ id: 2, name: '张三' }],
    ccUsers: [{ id: 3, name: '李四' }],
    participantIds: [2, 3],
    version: 0,
    category: { id: 2, name: '汇报' },
    tags: [{ id: 1, name: '重要' }],
    subtaskCompleted: 1,
    subtaskTotal: 2,
    files: [],
    createdAt: new Date().toISOString(),
  },
  {
    id: 2,
    title: '每日站会',
    content: '',
    status: 'ACTIVE',
    completed: false,
    priority: 'MEDIUM',
    dueAt: iso(0, 9),
    remindAt: null,
    cycleRule: { freq: 'DAILY', interval: 1, dtstart: iso(0, 9), tz: 'Asia/Shanghai', count: null, until: null, byDay: null },
    cycleLastCompleted: null,
    owner: { id: 1, name: '管理员' },
    assignees: [],
    ccUsers: [],
    participantIds: [],
    version: 0,
    category: null,
    tags: [],
    subtaskCompleted: 0,
    subtaskTotal: 0,
    files: [],
    createdAt: new Date().toISOString(),
  },
  {
    id: 3,
    title: '归档：上一版本上线',
    content: '已完成',
    status: 'ACTIVE',
    completed: true,
    priority: 'LOW',
    dueAt: iso(-2),
    remindAt: null,
    cycleRule: null,
    cycleLastCompleted: null,
    owner: { id: 1, name: '管理员' },
    assignees: [],
    ccUsers: [],
    participantIds: [],
    version: 1,
    category: null,
    tags: [{ id: 2, name: '跟进' }],
    subtaskCompleted: 0,
    subtaskTotal: 0,
    files: [],
    createdAt: new Date().toISOString(),
  },
]

function filterByScope(scope: string): MockTask[] {
  switch (scope) {
    case 'day':
      return TASKS.filter((t) => !t.completed)
    case 'week':
      return TASKS.filter((t) => !t.completed)
    case 'assigned':
      return TASKS.filter((t) => t.assignees.some((a) => a.id === 1))
    case 'joined':
      return TASKS.filter((t) => t.ccUsers.some((a) => a.id === 1))
    case 'collect':
      return TASKS.filter((t) => collected.has(t.id))
    default:
      return TASKS.filter((t) => t.owner.id === 1 || t.participantIds.length > 0)
  }
}

function pageOf(list: MockTask[], url: URL): Response {
  const page = Number(url.searchParams.get('page') ?? 1)
  const size = Number(url.searchParams.get('size') ?? 20)
  const start = (page - 1) * size
  return ok({ list: list.slice(start, start + size), total: list.length, page, size })
}

export const handlers = [
  http.post(`${AUTH}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string }
    if (body.username === 'admin' && body.password === 'admin123') {
      return ok({ accessToken: 'mock-access', refreshToken: 'mock-refresh', mustChangePassword: false })
    }
    return fail(20003, '用户名或密码错误')
  }),
  http.post(`${AUTH}/auth/refresh`, () => ok({ accessToken: 'mock-access', refreshToken: 'mock-refresh', mustChangePassword: false })),
  http.post(`${AUTH}/auth/logout`, () => ok(null)),
  http.get(`${AUTH}/auth/external/config`, () => ok({ provider: 'LOCAL' })),

  http.get(`${AUTH}/users/me`, () => ok(MOCK_USER)),
  http.get(`${AUTH}/users`, ({ request }) => {
    const url = new URL(request.url)
    const deptId = url.searchParams.get('deptId')
    const kw = (url.searchParams.get('keyword') ?? '').toLowerCase()
    let list = USERS
    if (deptId) list = list.filter((u) => String(u.deptId) === deptId)
    if (kw) list = list.filter((u) => u.username.includes(kw) || u.name.includes(kw))
    return ok({ list, total: list.length, page: 1, size: 100 })
  }),
  http.post(`${AUTH}/users`, () => ok(seq++)),
  http.put(`${AUTH}/users/:id/password`, () => ok(null)),
  http.put(`${AUTH}/users/:id`, () => ok(null)),
  http.delete(`${AUTH}/users/:id`, () => ok(null)),

  http.get(`${AUTH}/departments`, () => ok(DEPARTMENTS)),
  http.post(`${AUTH}/departments`, () => ok(seq++)),
  http.put(`${AUTH}/departments/:id`, () => ok(null)),
  http.delete(`${AUTH}/departments/:id`, () => ok(null)),
  http.get(`${AUTH}/org/sync/status`, () => ok({ running: false, lastSyncAt: null, lastResult: null, mode: 'NONE' })),
  http.post(`${AUTH}/org/sync`, () => ok('空操作')),

  http.get(`${AUTH}/categories`, ({ request }) => {
    const scope = new URL(request.url).searchParams.get('scope')
    return ok(scope ? CATEGORIES.filter((c) => c.scope === scope) : CATEGORIES)
  }),
  http.post(`${AUTH}/categories`, () => ok(seq++)),
  http.put(`${AUTH}/categories/:id`, () => ok(null)),
  http.delete(`${AUTH}/categories/:id`, () => ok(null)),

  http.get(`${AUTH}/tags`, () => ok(TAGS)),
  http.post(`${AUTH}/tags`, async ({ request }) => {
    const body = (await request.json()) as { name: string }
    const id = seq++
    TAGS.push({ id, name: body.name })
    return ok(id)
  }),
  http.put(`${AUTH}/tags/:id`, async ({ request, params }) => {
    const body = (await request.json()) as { name: string }
    const tag = TAGS.find((t) => String(t.id) === params.id)
    if (tag) tag.name = body.name
    return ok(null)
  }),
  http.delete(`${AUTH}/tags/:id`, ({ params }) => {
    const idx = TAGS.findIndex((t) => String(t.id) === params.id)
    if (idx >= 0) TAGS.splice(idx, 1)
    return ok(null)
  }),

  http.get(`${AUTH}/menus`, () => ok([])),
  http.post(`${AUTH}/menus`, () => ok(seq++)),
  http.put(`${AUTH}/menus/:id`, () => ok(null)),
  http.delete(`${AUTH}/menus/:id`, () => ok(null)),
  http.post(`${AUTH}/menus/:id/items`, () => ok(null)),
  http.delete(`${AUTH}/menus/:id/items/:itemId`, () => ok(null)),

  http.get(`${AUTH}/tasks/count`, () =>
    ok({
      day: filterByScope('day').length,
      week: filterByScope('week').length,
      joined: filterByScope('joined').length,
      assigned: filterByScope('assigned').length,
      collect: filterByScope('collect').length,
      all: filterByScope('all').length,
    }),
  ),
  http.get(`${AUTH}/tasks/calendar`, () => ok({ list: TASKS, total: TASKS.length, page: 1, size: TASKS.length })),
  http.get(`${AUTH}/tasks/subtasks`, () => ok({ list: [], total: 0, page: 1, size: 0 })),
  http.get(`${AUTH}/tasks/:id`, ({ params }) => {
    const task = TASKS.find((t) => String(t.id) === params.id)
    if (!task) return fail(30001, '任务不存在')
    return ok({ ...task, subtasks: [{ id: 9001, title: '收集数据', completed: true, version: 0, priority: 'MEDIUM', status: 'ACTIVE' }] })
  }),
  http.get(`${AUTH}/tasks`, ({ request }) => {
    const url = new URL(request.url)
    const scope = url.searchParams.get('scope') ?? 'all'
    const kw = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    let list = filterByScope(scope)
    if (kw) list = list.filter((t) => t.title.toLowerCase().includes(kw) || t.content.toLowerCase().includes(kw))
    return pageOf(list, url)
  }),
  http.post(`${AUTH}/tasks`, async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    const id = seq++
    TASKS.unshift({
      id,
      title: String(body.title ?? ''),
      content: String(body.content ?? ''),
      status: 'ACTIVE',
      completed: false,
      priority: String(body.priority ?? 'MEDIUM'),
      dueAt: (body.dueAt as string) ?? null,
      remindAt: (body.remindAt as string) ?? null,
      cycleRule: body.cycleRule ?? null,
      cycleLastCompleted: null,
      owner: { id: 1, name: '管理员' },
      assignees: [],
      ccUsers: [],
      participantIds: [],
      version: 0,
      category: null,
      tags: [],
      subtaskCompleted: 0,
      subtaskTotal: 0,
      files: [],
      createdAt: new Date().toISOString(),
    })
    return ok(id)
  }),
  http.put(`${AUTH}/tasks/:id`, async ({ request, params }) => {
    const task = TASKS.find((t) => String(t.id) === params.id)
    const body = (await request.json()) as { version?: number; title?: string }
    if (!task) return fail(30001, '任务不存在')
    if (body.version == null || body.version !== task.version) return fail(10006, '数据版本冲突')
    if (body.title) task.title = body.title
    task.version += 1
    return ok(null)
  }),
  http.delete(`${AUTH}/tasks/:id`, ({ params }) => {
    const idx = TASKS.findIndex((t) => String(t.id) === params.id)
    if (idx >= 0) TASKS.splice(idx, 1)
    return ok(null)
  }),
  http.post(`${AUTH}/tasks/:id/complete`, ({ params }) => {
    const task = TASKS.find((t) => String(t.id) === params.id)
    if (!task) return fail(30001, '任务不存在')
    if (task.cycleRule) task.cycleLastCompleted = new Date().toISOString()
    else task.completed = true
    task.version += 1
    return ok(null)
  }),
  http.post(`${AUTH}/tasks/:id/uncomplete`, ({ params }) => {
    const task = TASKS.find((t) => String(t.id) === params.id)
    if (!task) return fail(30001, '任务不存在')
    task.completed = false
    task.version += 1
    return ok(null)
  }),
  http.post(`${AUTH}/tasks/:id/collect`, ({ params }) => {
    collected.add(Number(params.id))
    return ok(null)
  }),
  http.delete(`${AUTH}/tasks/:id/collect`, ({ params }) => {
    collected.delete(Number(params.id))
    return ok(null)
  }),

  http.get(`${AUTH}/audit/operates`, () =>
    ok({
      list: [
        { id: 1, module: 'task', action: 'complete', uri: '/api/v1/tasks/1/complete', method: 'POST', userId: 1, ip: '127.0.0.1', userAgent: 'mock', duration: 12, status: 'SUCCESS', msg: null, createdAt: new Date().toISOString() },
      ],
      total: 1,
      page: 1,
      size: 20,
    }),
  ),
  http.get(`${AUTH}/audit/logins`, () =>
    ok({
      list: [{ id: 1, username: 'admin', success: true, ip: '127.0.0.1', userAgent: 'mock', createdAt: new Date().toISOString() }],
      total: 1,
      page: 1,
      size: 20,
    }),
  ),
]
