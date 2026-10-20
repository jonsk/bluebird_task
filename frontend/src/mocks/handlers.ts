import { http, HttpResponse } from 'msw'
import { dataOf, envelope } from './fixtures'

/**
 * MSW 契约 Mock（03 §2.6）—— **直连基线 fixtures**（`docs/frontend-baseline/fixtures/api/**`）。
 *
 * - 仅 dev/test 装载（`main.ts` 动态 import，生产 tree-shake）；端点按 `docs/api/openapi.yaml` 对齐。
 * - 读态**原样**返回 fixture 信封；写态在内存副本上变更（重启重置）；错误分支复用 `_errors/` 信封。
 * - 不再内联 mock 数据：fixture 即是 mock、单测与 E2E 的单一事实源（`fixtures/README.md §5`）。
 * - 已知 fixture 缺口（`fixtures/README.md §1.3`）：`PUT/DELETE /users/{id}`、部门写操作无 fixture，
 *   此处以最小 `{code:0}` 兜底。
 */

const AUTH = '/api/v1'

interface Named {
  id: number
  name: string
}
interface TaskLike {
  id: number
  title: string
  content?: string
  parentId?: number
  status?: string
  completed: boolean
  version?: number
  dueAt?: string | null
  remindAt?: string | null
  priority?: string
  cycleRule?: unknown
  cycleLastCompleted?: string | null
  owner?: Named | null
  assignees?: Named[]
  ccUsers?: Named[]
  participantIds?: number[]
  tags?: Named[]
  subtaskCompleted?: number
  subtaskTotal?: number
  files?: unknown[]
  createdAt?: string
}
interface PageLike<T> {
  list: T[]
  total: number
  page: number
  size: number
}
interface UserLike {
  id: number
  username: string
  name: string
  mobile?: string
  deptId?: number | null
  deptName?: string | null
  roleCode: string
}
interface TagLike extends Named {
  color?: string | null
}
interface MenuItemLike {
  id: number
  menuId: number
  taskId: number
  sort?: number
  task?: { id: number; title: string; completed?: boolean; dueAt?: string | null; priority?: string }
}
interface MenuLike {
  id: number
  userId: number
  name: string
  sort?: number
  createdAt?: string
  items: MenuItemLike[]
}

/** 成功态：就地组装（用于过滤/分页/内存变更后的返回）。 */
function ok<T>(data: T): Response {
  return HttpResponse.json({ code: 0, message: 'ok', data, traceId: 'mock-fixture' })
}
/** 原样透传 fixture 信封（读态 + 写态 + 错误态复用）。 */
function raw(key: string): Response {
  return HttpResponse.json(envelope(key))
}
/** 错误态：`_errors/<code>.<name>`（HTTP 200 + code!=0，02 §1.3）。 */
function fail(code: number, name: string): Response {
  return HttpResponse.json(envelope(`_errors/${code}.${name}`))
}

const ME = dataOf<UserLike>('users/GET.me')
const ME_ID = Number(ME.id)
const ME_NAME = ME.name ?? ME.username

const usersPage = dataOf<PageLike<UserLike>>('users/GET.index')
const users: UserLike[] = structuredClone(usersPage.list)

const taskPage = dataOf<PageLike<TaskLike>>('tasks/GET.list')
const tasks: TaskLike[] = structuredClone(taskPage.list)
const defaultSize = taskPage.size

const departments = dataOf<unknown[]>('departments/GET.tree')
const tags: TagLike[] = structuredClone(dataOf<TagLike[]>('tags/GET.list'))
const menus: MenuLike[] = structuredClone(dataOf<MenuLike[]>('menus/GET.list'))
const detail = dataOf<TaskLike & { subtasks?: TaskLike[] }>('tasks/GET.detail')
const subtaskList = dataOf<PageLike<TaskLike>>('tasks/GET.subtasks').list

/** 在主列表、详情子任务、子任务列表 fixture 中查找任务（完成/子任务状态切换需覆盖子任务）。 */
function findAnyTask(id: number): TaskLike | undefined {
  return (
    tasks.find((t) => t.id === id) ??
    (detail.subtasks ?? []).find((t) => t.id === id) ??
    subtaskList.find((t) => t.id === id)
  )
}

/* 附件内存库：初始化为详情 fixture 的附件，上传/删除就地变更（item 10：文件名用用户所选文件、可删除）。 */
interface FileLike {
  id: number
  fileName: string
  size: number
  md5?: string
}
const fileStore: FileLike[] = structuredClone((detail.files ?? []) as FileLike[])
let fileSeq = 9000
function mergeFiles(base: unknown[]): FileLike[] {
  const seen = new Set<number>(base.map((f) => Number((f as FileLike).id)))
  return [...(base as FileLike[]), ...fileStore.filter((f) => !seen.has(Number(f.id)))]
}

/* ---------- 分类树：内存 CRUD + 子树/范围 ---------- */

/** 收藏集：与计数 fixture（collect=1）一致，预置首个任务。 */
const collected = new Set<number>(tasks.length ? [tasks[0].id] : [])

let seq = 200

function involvesMe(t: TaskLike): boolean {
  return (
    t.owner?.id === ME_ID ||
    (t.assignees ?? []).some((a) => a.id === ME_ID) ||
    (t.ccUsers ?? []).some((a) => a.id === ME_ID) ||
    (t.participantIds ?? []).includes(ME_ID)
  )
}

function filterByScope(scope: string): TaskLike[] {
  switch (scope) {
    case 'day':
    case 'week':
      return tasks.filter((t) => !t.completed)
    case 'joined':
      return tasks.filter((t) => (t.ccUsers ?? []).some((a) => a.id === ME_ID))
    case 'assigned':
      return tasks.filter((t) => (t.assignees ?? []).some((a) => a.id === ME_ID))
    case 'collect':
      return tasks.filter((t) => collected.has(t.id))
    default:
      return tasks.filter(involvesMe)
  }
}

function pageOf(list: TaskLike[], url: URL): Response {
  const page = Number(url.searchParams.get('page') ?? 1)
  const size = Number(url.searchParams.get('size') ?? defaultSize)
  const start = (page - 1) * size
  return ok({ list: list.slice(start, start + size), total: list.length, page, size })
}

// ==================== 后台（配置中心 / 同步日志）mock ====================

interface LoginLogLike {
  id: number
  username: string
  success: boolean
  ip?: string
  userAgent?: string
  provider?: string | null
  eventType?: string | null
  externalId?: string | null
  createdAt?: string
}

interface MockConfigItem {
  key: string
  label: string
  value: string
  type: 'STRING' | 'INT' | 'BOOL' | 'SELECT' | 'JSON'
  secret: boolean
  hasSecret: boolean
  source: 'DB' | 'YML' | 'DEFAULT'
  description: string
  options: string[]
  requiresRestart: boolean
}

interface MockConfigGroup {
  group: string
  label: string
  description: string
  items: MockConfigItem[]
}

function item(
  key: string,
  label: string,
  value: string,
  type: MockConfigItem['type'] = 'STRING',
  extra: Partial<MockConfigItem> = {},
): MockConfigItem {
  return {
    key,
    label,
    value,
    type,
    secret: false,
    hasSecret: false,
    source: 'DEFAULT',
    description: '',
    options: [],
    requiresRestart: false,
    ...extra,
  }
}

/**
 * 与后端 `ConfigDefinition` 对齐的 mock 配置（`docs/api/openapi.yaml` 契约）。
 * 保持可变：`PUT /admin/config/{group}` 在内存中改值，供后台页面联调与视觉回归。
 */
const configState: MockConfigGroup[] = [
  {
    group: 'casdoor',
    label: 'Casdoor 对接',
    description: '统一身份源连接参数；client-secret 加密存储，读取时只返回掩码',
    items: [
      item('endpoint', '服务地址', 'https://casdoor.example.com'),
      item('client-id', 'Client ID', 'bluebird-client'),
      item('client-secret', 'Client Secret', '******', 'STRING', { secret: true, hasSecret: true }),
      item('organization', '组织名', 'bluebird'),
      item('application-name', '应用名', 'bluebird-task'),
      item('redirect-uri', '回调地址', 'http://localhost:8080/api/v1/auth/oidc/callback'),
      item('scopes', '授权范围', 'openid profile email phone'),
      item('role-map', '角色映射', '{"bluebird-admin":"ADMIN","bluebird-common":"COMMON"}', 'JSON'),
      item('dept-source', '部门来源', 'groups', 'SELECT', { options: ['groups', 'organizations'] }),
    ],
  },
  {
    group: 'identity',
    label: '登录方式',
    description: '决定登录页展示与账密登录是否对普通用户开放',
    items: [
      item('provider', '认证方式', 'LOCAL', 'SELECT', { options: ['LOCAL', 'OIDC'] }),
      item('auto-create-user', '自动建用户', 'true', 'BOOL'),
    ],
  },
  {
    group: 'orgsync',
    label: '组织同步',
    description: 'Casdoor → 本地用户/部门/角色 的拉取策略',
    items: [
      item('mode', '同步模式', 'PULL', 'SELECT', { options: ['NONE', 'PULL', 'PUSH', 'SCIM'] }),
      item('adapter', '适配器', 'casdoor', 'SELECT', { options: ['casdoor'] }),
      item('cron', '同步时间（cron）', '0 0 1 * * *'),
      item('batch-size', '分页大小', '100', 'INT'),
    ],
  },
  {
    group: 'security',
    label: '登录安全',
    description: '账密登录防暴破参数，修改后即时生效',
    items: [
      item('login-max-fail', '最大失败次数', '5', 'INT'),
      item('login-lock-seconds', '锁定时长（秒）', '900', 'INT'),
    ],
  },
  {
    group: 'audit',
    label: '审计',
    description: '日志留存策略',
    items: [item('retention-days', '日志保留天数', '180', 'INT')],
  },
  {
    group: 'storage',
    label: '文件存储',
    description: '上传限制；修改后需重启',
    items: [
      item('max-size', '单文件上限', '50MB', 'STRING', { requiresRestart: true }),
      item('allowed-ext', '允许的扩展名', '', 'STRING', { requiresRestart: true }),
    ],
  },
]

function configGroups(): MockConfigGroup[] {
  return configState
}

function orgSyncLogs() {
  return [
    {
      id: 2,
      source: 'casdoor',
      syncType: 'PULL',
      status: 'SUCCESS',
      startedAt: '2026-09-28T01:00:00+08:00',
      finishedAt: '2026-09-28T01:00:02+08:00',
      total: 4,
      inserted: 0,
      updated: 4,
      skipped: 0,
      errorMessage: null,
    },
    {
      id: 1,
      source: 'casdoor',
      syncType: 'PULL',
      status: 'FAILURE',
      startedAt: '2026-09-27T01:00:00+08:00',
      finishedAt: '2026-09-27T01:00:05+08:00',
      total: 0,
      inserted: 0,
      updated: 0,
      skipped: 0,
      errorMessage: 'connect timed out',
    },
  ]
}

export const handlers = [
  // ---- auth ----
  http.post(`${AUTH}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string }
    if (body.username === ME.username && body.password === 'admin123') {
      return raw('auth/POST.login')
    }
    return fail(20003, 'bad-credentials')
  }),
  http.post(`${AUTH}/auth/refresh`, () => raw('auth/POST.refresh')),
  http.post(`${AUTH}/auth/logout`, () => raw('auth/POST.logout')),
  http.get(`${AUTH}/auth/external/config`, () => raw('auth/GET.external-config')),

  // ---- users ----
  http.get(`${AUTH}/users/me`, () => raw('users/GET.me')),
  http.get(`${AUTH}/users`, ({ request }) => {
    const url = new URL(request.url)
    const deptId = url.searchParams.get('deptId')
    const kw = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    let list = users
    if (deptId) list = list.filter((u) => String(u.deptId) === deptId)
    if (kw) list = list.filter((u) => u.username.toLowerCase().includes(kw) || u.name.toLowerCase().includes(kw))
    return ok({ list, total: list.length, page: 1, size: usersPage.size })
  }),
  http.post(`${AUTH}/users`, () => raw('users/POST.create')),
  http.put(`${AUTH}/users/:id/password`, () => raw('users/PUT.password')),
  // 已知缺口：无 users/PUT.update / DELETE.remove fixture（fixtures/README §1.3）
  http.put(`${AUTH}/users/:id`, () => ok(null)),
  http.delete(`${AUTH}/users/:id`, () => ok(null)),

  // ---- departments ----
  http.get(`${AUTH}/departments`, () => ok(departments)),
  // 已知缺口：部门写操作无 fixture
  http.post(`${AUTH}/departments`, () => ok(seq++)),
  http.put(`${AUTH}/departments/:id`, () => ok(null)),
  http.delete(`${AUTH}/departments/:id`, () => ok(null)),
  http.get(`${AUTH}/org/sync/status`, () => ok({ running: false, lastSyncAt: null, lastResult: null, mode: 'NONE' })),
  http.post(`${AUTH}/org/sync`, () => ok('空操作')),

  // ---- tags ----
  http.get(`${AUTH}/tags`, () => ok(tags)),
  http.post(`${AUTH}/tags`, async ({ request }) => {
    const body = (await request.json()) as { name?: string }
    const created = dataOf<TagLike>('tags/POST.create')
    if (!tags.some((t) => t.id === created.id)) tags.push({ id: created.id, name: body.name ?? created.name })
    return raw('tags/POST.create')
  }),
  http.put(`${AUTH}/tags/:id`, () => raw('tags/PUT.update')),
  http.delete(`${AUTH}/tags/:id`, ({ params }) => {
    const idx = tags.findIndex((t) => String(t.id) === params.id)
    if (idx >= 0) tags.splice(idx, 1)
    return raw('tags/DELETE.remove')
  }),

  // ---- menus（自定义栏） ----
  http.get(`${AUTH}/menus`, () => ok(menus)),
  http.post(`${AUTH}/menus`, async ({ request }) => {
    const body = (await request.json()) as { name?: string; sort?: number }
    // 每次新建分配递增 id（item 4：多个自定义栏）。固定 fixture id 会导致重复栏不生效。
    const id = seq++
    menus.push({
      id,
      userId: ME_ID,
      name: body.name ?? '新栏',
      sort: body.sort ?? 0,
      createdAt: new Date().toISOString(),
      items: [],
    })
    return ok({ id })
  }),
  http.put(`${AUTH}/menus/:id`, async ({ request, params }) => {
    const menu = menus.find((m) => String(m.id) === params.id)
    const body = (await request.json()) as { name?: string; sort?: number }
    if (menu) {
      if (body.name) menu.name = body.name
      if (typeof body.sort === 'number') menu.sort = body.sort
    }
    return raw('menus/PUT.update')
  }),
  http.delete(`${AUTH}/menus/:id`, ({ params }) => {
    const i = menus.findIndex((m) => String(m.id) === params.id)
    if (i >= 0) menus.splice(i, 1)
    return raw('menus/DELETE.remove')
  }),
  http.post(`${AUTH}/menus/:id/items`, async ({ request, params }) => {
    const menu = menus.find((m) => String(m.id) === params.id)
    const body = (await request.json()) as { taskId?: number }
    if (menu && body.taskId != null && !menu.items.some((it) => it.taskId === body.taskId)) {
      const t = tasks.find((x) => x.id === body.taskId)
      menu.items.push({
        id: seq++,
        menuId: menu.id,
        taskId: body.taskId,
        sort: menu.items.length + 1,
        task: t
          ? { id: t.id, title: t.title, completed: t.completed, dueAt: t.dueAt, priority: t.priority }
          : undefined,
      })
    }
    return raw('menus/POST.items')
  }),
  http.delete(`${AUTH}/menus/:id/items/:itemId`, ({ params }) => {
    const menu = menus.find((m) => String(m.id) === params.id)
    if (menu) {
      const i = menu.items.findIndex((it) => String(it.id) === params.itemId)
      if (i >= 0) menu.items.splice(i, 1)
    }
    return raw('menus/DELETE.items')
  }),

  // ---- tasks（注意：count/calendar/subtasks 必须先于 :id 注册） ----
  http.get(`${AUTH}/tasks/count`, () => raw('tasks/GET.count')),
  http.get(`${AUTH}/tasks/calendar`, () => raw('tasks/GET.calendar')),
  http.get(`${AUTH}/tasks/subtasks`, () => raw('tasks/GET.subtasks')),
  http.get(`${AUTH}/tasks`, ({ request }) => {
    const url = new URL(request.url)
    const scope = url.searchParams.get('scope') ?? 'all'
    const kw = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    let list = filterByScope(scope)
    if (kw) {
      list = list.filter(
        (t) => t.title.toLowerCase().includes(kw) || (t.content ?? '').toLowerCase().includes(kw),
      )
    }
    // 自定义栏过滤
    const menuId = url.searchParams.get('menuId')
    if (menuId) {
      const menu = menus.find((m) => String(m.id) === menuId)
      const taskIds = new Set((menu?.items ?? []).map((it) => it.taskId))
      list = list.filter((t) => taskIds.has(t.id))
    }
    return pageOf(list, url)
  }),
  http.post(`${AUTH}/tasks`, async ({ request }) => {
    const created = dataOf<{ id: number }>('tasks/POST.create')
    const body = (await request.json()) as Partial<TaskLike>
    if (!tasks.some((t) => t.id === created.id)) {
      const createdTask: TaskLike = {
        id: created.id,
        title: String(body.title ?? ''),
        content: String(body.content ?? ''),
        status: 'ACTIVE',
        completed: false,
        version: 0,
        dueAt: body.dueAt ?? null,
        remindAt: body.remindAt ?? null,
        priority: String(body.priority ?? 'MEDIUM'),
        cycleRule: body.cycleRule ?? null,
        cycleLastCompleted: null,
        owner: { id: ME_ID, name: ME_NAME },
        assignees: [],
        ccUsers: [],
        participantIds: [ME_ID],
        tags: [],
        subtaskCompleted: 0,
        subtaskTotal: 0,
        files: [],
        createdAt: new Date().toISOString(),
      }
      tasks.unshift(createdTask)
      // 子任务：追加到详情 subtasks，供详情面板展示（item 8）
      if (body.parentId != null && Number(body.parentId) === detail.id) {
        detail.subtasks ??= []
        detail.subtasks.push(createdTask)
      }
    }
    return raw('tasks/POST.create')
  }),
  http.get(`${AUTH}/tasks/:id`, ({ params }) => {
    const task = tasks.find((t) => String(t.id) === params.id)
    const isDetail = String(detail.id) === params.id
    // 详情 fixture 含 files/tags/subtasks，列表 fixture 不含 → 以详情为基座，
    // 仅叠加内存中可变字段（避免列表 fixture 的空 files 覆盖详情）。
    const base = isDetail ? detail : task
    if (!base) return raw('tasks/GET.detail')
    const overlay = task
      ? {
          title: task.title,
          content: task.content,
          completed: task.completed,
          version: task.version,
          dueAt: task.dueAt,
          remindAt: task.remindAt,
          priority: task.priority,
          cycleLastCompleted: task.cycleLastCompleted,
        }
      : {}
    return ok({
      ...base,
      ...overlay,
      subtasks: isDetail ? (detail.subtasks ?? []) : subtaskList,
      files: mergeFiles(base.files ?? []),
    })
  }),
  http.put(`${AUTH}/tasks/:id`, async ({ request, params }) => {
    const task = tasks.find((t) => String(t.id) === params.id)
    const body = (await request.json()) as Partial<TaskLike> & { version?: number }
    if (!task) return fail(30001, 'task-not-found')
    if (typeof body.version === 'number' && body.version !== (task.version ?? 0)) {
      return fail(10006, 'version-conflict')
    }
    Object.assign(task, body, { version: (task.version ?? 0) + 1 })
    return raw('tasks/PUT.update')
  }),
  http.delete(`${AUTH}/tasks/:id`, ({ params }) => {
    const idx = tasks.findIndex((t) => String(t.id) === params.id)
    if (idx < 0) return fail(30001, 'task-not-found')
    tasks.splice(idx, 1)
    return raw('tasks/DELETE.remove')
  }),
  http.post(`${AUTH}/tasks/:id/complete`, ({ params }) => {
    const task = findAnyTask(Number(params.id))
    if (!task) return fail(30001, 'task-not-found')
    if (task.cycleRule) task.cycleLastCompleted = new Date().toISOString()
    else task.completed = true
    task.version = (task.version ?? 0) + 1
    return raw('tasks/POST.complete')
  }),
  http.post(`${AUTH}/tasks/:id/uncomplete`, ({ params }) => {
    const task = findAnyTask(Number(params.id))
    if (!task) return fail(30001, 'task-not-found')
    task.completed = false
    task.version = (task.version ?? 0) + 1
    return raw('tasks/POST.uncomplete')
  }),
  http.post(`${AUTH}/tasks/:id/collect`, ({ params }) => {
    collected.add(Number(params.id))
    return raw('tasks/POST.collect')
  }),
  http.delete(`${AUTH}/tasks/:id/collect`, ({ params }) => {
    collected.delete(Number(params.id))
    return raw('tasks/DELETE.collect')
  }),

  // ---- files ----
  // item 10：上传回显用户所选文件（文件名/大小），不再固定返回 fixture 的「季度报告.pdf」
  http.post(`${AUTH}/files`, async ({ request }) => {
    const form = await request.formData()
    const file = form.get('file')
    const fileName = file instanceof File ? file.name : '未命名文件'
    const size = file instanceof File ? file.size : 0
    const stored: FileLike = { id: fileSeq++, fileName, size }
    fileStore.push(stored)
    return ok(stored)
  }),
  // item 10：允许删除上传的附件（内存移除）
  http.delete(`${AUTH}/files/:id`, ({ params }) => {
    const idx = fileStore.findIndex((f) => String(f.id) === params.id)
    if (idx >= 0) fileStore.splice(idx, 1)
    return ok(null)
  }),

  // ---- audit ----
  http.get(`${AUTH}/audit/operates`, () => raw('audit/GET.operates')),
  http.get(`${AUTH}/audit/logins`, ({ request }) => {
    const url = new URL(request.url)
    const provider = url.searchParams.get('provider')
    const eventType = url.searchParams.get('eventType')
    const base = dataOf<PageLike<LoginLogLike>>('audit/GET.logins')
    let list = base.list ?? []
    if (provider) list = list.filter((r) => (r.provider ?? 'LOCAL') === provider)
    if (eventType) list = list.filter((r) => (r.eventType ?? 'LOGIN') === eventType)
    return ok({ ...base, list, total: list.length })
  }),

  // ---- admin（配置中心 / 同步与任务日志）----
  http.get(`${AUTH}/admin/config`, () => ok(configGroups())),
  http.get(`${AUTH}/admin/config/:group`, ({ params }) => {
    const g = configGroups().find((x) => x.group === params.group)
    return g ? ok(g) : fail(10004, '配置分组不存在')
  }),
  http.put(`${AUTH}/admin/config/:group`, async ({ request, params }) => {
    const body = (await request.json()) as { values?: Record<string, string | null> }
    const group = configGroups().find((x) => x.group === params.group)
    if (!group) return fail(10004, '配置分组不存在')
    for (const item of group.items) {
      const v = body.values?.[item.key]
      if (v === undefined || v === null || v === '******') continue
      item.value = v
      item.source = v === '' ? 'DEFAULT' : 'DB'
      if (item.secret && v) item.hasSecret = true
    }
    return ok(group)
  }),
  http.post(`${AUTH}/admin/config/casdoor/test`, () =>
    ok({
      ok: true,
      message: '连接正常',
      latencyMs: 42,
      issuer: 'https://casdoor.example.com',
      authorizationEndpoint: 'https://casdoor.example.com/login/oauth/authorize',
      tokenEndpoint: 'https://casdoor.example.com/api/login/oauth/access_token',
    }),
  ),
  http.get(`${AUTH}/org/sync/logs`, () => ok({ list: orgSyncLogs(), total: orgSyncLogs().length, page: 1, size: 20 })),
  http.get(`${AUTH}/org/sync/latest`, () => ok(orgSyncLogs()[0] ?? null)),
  http.get(`${AUTH}/jobs/logs`, () =>
    ok({
      list: [
        {
          id: 1,
          job: 'org-sync',
          success: true,
          duration: 1280,
          message: '拉取 4 名用户、3 个部门',
          startedAt: '2026-09-28T01:00:00+08:00',
          finishedAt: '2026-09-28T01:00:02+08:00',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    }),
  ),
]
