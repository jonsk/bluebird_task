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
  category?: Named | null
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
interface CategoryLike extends Named {
  parentId?: number | null
  scope?: string
  deptId?: number | null
  ownerId?: number | null
  sort?: number
  taskCount?: number
  createdAt?: string
  updatedAt?: string
  children?: CategoryLike[]
}
interface TagLike extends Named {
  color?: string | null
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
const categories = dataOf<CategoryLike[]>('categories/GET.tree')
const tags: TagLike[] = structuredClone(dataOf<TagLike[]>('tags/GET.list'))
const menus = dataOf<unknown[]>('menus/GET.list')
const detail = dataOf<TaskLike & { subtasks?: TaskLike[] }>('tasks/GET.detail')
const subtaskList = dataOf<PageLike<TaskLike>>('tasks/GET.subtasks').list

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

  // ---- categories ----
  http.get(`${AUTH}/categories`, ({ request }) => {
    const scope = new URL(request.url).searchParams.get('scope')
    return ok(scope ? categories.filter((c) => c.scope === scope) : categories)
  }),
  http.post(`${AUTH}/categories`, () => raw('categories/POST.create')),
  http.put(`${AUTH}/categories/:id`, () => raw('categories/PUT.update')),
  http.delete(`${AUTH}/categories/:id`, () => raw('categories/DELETE.remove')),

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
  http.post(`${AUTH}/menus`, () => raw('menus/POST.create')),
  http.put(`${AUTH}/menus/:id`, () => raw('menus/PUT.update')),
  http.delete(`${AUTH}/menus/:id`, () => raw('menus/DELETE.remove')),
  http.post(`${AUTH}/menus/:id/items`, () => raw('menus/POST.items')),
  http.delete(`${AUTH}/menus/:id/items/:itemId`, () => raw('menus/DELETE.items')),

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
    return pageOf(list, url)
  }),
  http.post(`${AUTH}/tasks`, async ({ request }) => {
    const created = dataOf<{ id: number }>('tasks/POST.create')
    const body = (await request.json()) as Partial<TaskLike>
    if (!tasks.some((t) => t.id === created.id)) {
      tasks.unshift({
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
        category: null,
        tags: [],
        subtaskCompleted: 0,
        subtaskTotal: 0,
        files: [],
        createdAt: new Date().toISOString(),
      })
    }
    return raw('tasks/POST.create')
  }),
  http.get(`${AUTH}/tasks/:id`, ({ params }) => {
    const task = tasks.find((t) => String(t.id) === params.id)
    const isDetail = String(detail.id) === params.id
    // 详情 fixture 含 files/category/tags/subtasks，列表 fixture 不含 → 以详情为基座，
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
    const task = tasks.find((t) => String(t.id) === params.id)
    if (!task) return fail(30001, 'task-not-found')
    if (task.cycleRule) task.cycleLastCompleted = new Date().toISOString()
    else task.completed = true
    task.version = (task.version ?? 0) + 1
    return raw('tasks/POST.complete')
  }),
  http.post(`${AUTH}/tasks/:id/uncomplete`, ({ params }) => {
    const task = tasks.find((t) => String(t.id) === params.id)
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
  http.post(`${AUTH}/files`, () => raw('files/POST.upload')),

  // ---- audit ----
  http.get(`${AUTH}/audit/operates`, () => raw('audit/GET.operates')),
  http.get(`${AUTH}/audit/logins`, () => raw('audit/GET.logins')),
]
