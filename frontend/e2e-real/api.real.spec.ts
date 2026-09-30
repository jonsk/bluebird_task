import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'

/**
 * 真实后端 API 联调用例（选入式，**不在默认 CI 中运行**）。
 *
 * 运行：先启动 jar（默认 `http://127.0.0.1:8080`，账号 `admin` / `Admin123!`），然后
 *   pnpm test:e2e:real                 # 全部（API + UI）
 *   pnpm test:e2e:real -- api         # 只跑 API
 * 可用 `E2E_REAL_BASE_URL` / `E2E_REAL_PASSWORD` 覆盖。
 *
 * 为什么需要它：默认 `pnpm test:e2e` 全部走 MSW fixtures（小 id、内存态），
 * 曾因此漏掉 4 个阻断级缺陷——snowflake id 超 2^53 导致按 id 操作全废、
 * 分类树 NPE、附件上传 10000、连续上传被误判重复提交。这些只有在真实后端才暴露。
 */

const BASE = process.env.E2E_REAL_BASE_URL ?? 'http://127.0.0.1:8080'
const PASSWORD = process.env.E2E_REAL_PASSWORD ?? 'Admin123!'
const MAX_SAFE = Number.MAX_SAFE_INTEGER
const uniq = (p: string) => `${p}${String(Date.now()).slice(-7)}`

test.use({ baseURL: BASE })

interface Ctx {
  token: string
  userId: number
}

async function login(request: APIRequestContext, username = 'admin', password = PASSWORD): Promise<Ctx> {
  const res = await request.post('/api/v1/auth/login', { data: { username, password } })
  const body = await res.json()
  expect(body.code, `登录失败：${JSON.stringify(body)}`).toBe(0)
  return { token: body.data.accessToken, userId: body.data.user.id }
}

const auth = (c: Ctx) => ({ Authorization: `Bearer ${c.token}` })

async function body(res: APIResponse): Promise<{ code: number; message?: string; data?: any }> {
  return res.json()
}

test.describe('真实后端 · API 联调', () => {
  let ctx: Ctx

  test.beforeAll(async ({ request }) => {
    ctx = await login(request)
  })

  test('登录与鉴权：成功 / 密码错误 / 未登录', async ({ request }) => {
    const ok = await body(await request.post('/api/v1/auth/login', { data: { username: 'admin', password: PASSWORD } }))
    expect(ok.code).toBe(0)
    expect(ok.data.accessToken).toBeTruthy()

    const bad = await body(await request.post('/api/v1/auth/login', { data: { username: 'admin', password: 'wrong-password' } }))
    expect(bad.code).toBe(20003)

    const anon = await body(await request.get('/api/v1/tasks?scope=all'))
    expect(anon.code).toBe(10002)

    const me = await body(await request.get('/api/v1/users/me', { headers: auth(ctx) }))
    expect(me.code).toBe(0)
    // S1：不得回传密码哈希
    expect(JSON.stringify(me.data)).not.toContain('$2a$')
  })

  test('主键为 JS 安全整数（ADR-017 回归护栏）', async ({ request }) => {
    expect(ctx.userId).toBeLessThanOrEqual(MAX_SAFE)

    const created = await body(
      await request.post('/api/v1/tasks', { headers: auth(ctx), data: { title: uniq('API-ID-'), assigneeIds: [ctx.userId] } }),
    )
    expect(created.code).toBe(0)
    const taskId: number = created.data
    expect(taskId).toBeLessThanOrEqual(MAX_SAFE)
    // 浏览器 JSON.parse 回环必须无损（前端全部依赖此性质）
    expect(JSON.parse(`{"id":${taskId}}`).id).toBe(taskId)

    const detail = await body(await request.get(`/api/v1/tasks/${taskId}`, { headers: auth(ctx) }))
    expect(detail.code, '按 id 取详情必须成功（曾是精度丢失导致 404）').toBe(0)

    await request.delete(`/api/v1/tasks/${taskId}`, { headers: auth(ctx) })
  })

  test('任务：子任务、完成/回退、乐观锁、六个视图、计数一致', async ({ request }) => {
    const parent = await body(
      await request.post('/api/v1/tasks', {
        headers: auth(ctx),
        data: { title: uniq('API-P-'), assigneeIds: [ctx.userId], ccIds: [ctx.userId] },
      }),
    )
    expect(parent.code).toBe(0)
    const pid: number = parent.data

    const sub = await body(await request.post('/api/v1/tasks', { headers: auth(ctx), data: { title: uniq('API-S-'), parentId: pid } }))
    expect(sub.code).toBe(0)
    const sid: number = sub.data

    const detail = await body(await request.get(`/api/v1/tasks/${pid}`, { headers: auth(ctx) }))
    const embedded = (detail.data.subtasks ?? []).find((s: any) => s.id === sid)
    expect(embedded, '详情须内嵌子任务且带 version').toBeTruthy()
    expect(embedded.version).not.toBeUndefined()

    // 子任务改状态（用户反馈 #11）
    const done = await body(await request.post(`/api/v1/tasks/${sid}/complete`, { headers: auth(ctx), data: { version: embedded.version } }))
    expect(done.code).toBe(0)
    const after = await body(await request.get(`/api/v1/tasks/${sid}`, { headers: auth(ctx) }))
    expect(after.data.completed).toBe(true)
    const undone = await body(
      await request.post(`/api/v1/tasks/${sid}/uncomplete`, { headers: auth(ctx), data: { version: after.data.version } }),
    )
    expect(undone.code).toBe(0)

    // 缺 version → 参数错误（ADR-013 契约）
    const noVer = await body(await request.post(`/api/v1/tasks/${sid}/complete`, { headers: auth(ctx), data: {} }))
    expect(noVer.code).toBe(10001)

    // 乐观锁：过期 version 必须 10006
    const stale = await body(await request.put(`/api/v1/tasks/${pid}`, { headers: auth(ctx), data: { title: 'stale', version: 999 } }))
    expect(stale.code).toBe(10006)

    // 视图与计数
    for (const scope of ['day', 'week', 'joined', 'assigned', 'collect', 'all']) {
      const v = await body(await request.get(`/api/v1/tasks?scope=${scope}`, { headers: auth(ctx) }))
      expect(v.code, `scope=${scope}`).toBe(0)
    }
    const assigned = await body(await request.get('/api/v1/tasks?scope=assigned', { headers: auth(ctx) }))
    const counts = await body(await request.get('/api/v1/tasks/count', { headers: auth(ctx) }))
    expect((assigned.data.list ?? []).some((t: any) => t.id === pid), '指派给我的任务必须出现在列表（用户反馈 #7）').toBe(true)
    expect(counts.data.assigned).toBe((assigned.data.list ?? []).length)

    // 参数类型错误 → 10001（不是 10000 系统异常）
    const badParam = await body(await request.get('/api/v1/tasks?scope=all&categoryId=NaN', { headers: auth(ctx) }))
    expect(badParam.code).toBe(10001)

    await request.delete(`/api/v1/tasks/${pid}`, { headers: auth(ctx) })
  })

  test('附件：上传（保留真实文件名/不限类型/可连发）与删除', async ({ request }) => {
    const names = ['API-真实文件名.pdf', 'API-无扩展名', 'API-脚本.ps1']
    const ids: number[] = []
    for (const name of names) {
      const res = await request.post('/api/v1/files', {
        headers: auth(ctx),
        multipart: { file: { name, mimeType: 'application/octet-stream', buffer: Buffer.from(`content of ${name}`) } },
      })
      const b = await body(res)
      expect(b.code, `上传 ${name} 应成功（曾因 @MapperScan 过度扫描报 10000）`).toBe(0)
      expect(b.data.fileName, '必须保留用户上传的真实文件名').toBe(name)
      ids.push(b.data.id)
    }
    expect(new Set(ids).size, '连续上传必须各自成功（曾因 multipart 防重 key 相同被 10005 拒绝）').toBe(names.length)

    for (const id of ids) {
      const del = await body(await request.delete(`/api/v1/files/${id}`, { headers: auth(ctx) }))
      expect(del.code, '用户上传的附件必须可删除（用户反馈 #10）').toBe(0)
    }
  })

  test('组织：系统默认部门可改名不可删 + 新建用户必填部门 + 手机号/邮箱', async ({ request }) => {
    const depts = await body(await request.get('/api/v1/departments', { headers: auth(ctx) }))
    expect(depts.code).toBe(0)
    const sys = (depts.data ?? []).find((d: any) => d.system === true)
    expect(sys, '必须存在 system=true 的默认顶级部门（用户反馈 #5）').toBeTruthy()

    const del = await body(await request.delete(`/api/v1/departments/${sys.id}`, { headers: auth(ctx) }))
    expect(del.code, '默认部门必须不可删除').not.toBe(0)
    const ren = await body(await request.put(`/api/v1/departments/${sys.id}`, { headers: auth(ctx), data: { name: sys.name } }))
    expect(ren.code, '默认部门必须可改名').toBe(0)

    const noDept = await body(
      await request.post('/api/v1/users', { headers: auth(ctx), data: { username: uniq('nodept'), name: '无部门', password: 'Xx123!' } }),
    )
    expect(noDept.code, '新建用户不传 deptId 必须被拒').toBe(10001)

    const uname = uniq('apiuser')
    const created = await body(
      await request.post('/api/v1/users', {
        headers: auth(ctx),
        data: { username: uname, name: '接口用户', password: 'Xx123!', deptId: sys.id, roleCode: 'COMMON', mobile: '13800001234', email: 'api@example.com' },
      }),
    )
    expect(created.code).toBe(0)
    const uid: number = created.data

    const row = ((await body(await request.get(`/api/v1/users?keyword=${uname}`, { headers: auth(ctx) }))).data.list ?? []).find(
      (u: any) => u.username === uname,
    )
    expect(row.mobile, 'ADMIN 应拿到明文手机号（否则无法编辑，用户反馈 #8）').toBe('13800001234')
    expect(row.email).toBe('api@example.com')

    await request.put(`/api/v1/users/${uid}`, { headers: auth(ctx), data: { name: '接口用户', deptId: sys.id, roleCode: 'COMMON', mobile: '', email: '' } })
    const cleared = ((await body(await request.get(`/api/v1/users?keyword=${uname}`, { headers: auth(ctx) }))).data.list ?? []).find(
      (u: any) => u.username === uname,
    )
    expect(cleared.mobile ?? null, '空串应清空手机号').toBeNull()
    expect(cleared.email ?? null, '空串应清空邮箱').toBeNull()

    await request.delete(`/api/v1/users/${uid}`, { headers: auth(ctx) })
  })

  test('分类：个人/部门分类可建，部门分类须指定部门', async ({ request }) => {
    const tree0 = await body(await request.get('/api/v1/departments', { headers: auth(ctx) }))
    const deptId = (tree0.data ?? [])[0]?.id
    expect(deptId).toBeTruthy()

    // 省略 deptId 时的契约：调用者**有**部门 → 回落到该部门；调用者**无**部门 → 10001。
    // （默认部门兜底后 admin 也有部门，故这里按当前调用者状态分别断言。）
    const me = await body(await request.get('/api/v1/users/me', { headers: auth(ctx) }))
    const myDept = me.data.deptId ?? null
    const omitted = await body(await request.post('/api/v1/categories', { headers: auth(ctx), data: { name: uniq('省略部门-'), scope: 'DEPARTMENT' } }))
    if (myDept == null) {
      expect(omitted.code, '调用者无部门时必须显式指定 deptId').toBe(10001)
    } else {
      expect(omitted.code, '省略 deptId 应回落到调用者所在部门').toBe(0)
      await request.delete(`/api/v1/categories/${omitted.data}`, { headers: auth(ctx) })
    }

    const name = uniq('API-部门分类')
    const created = await body(
      await request.post('/api/v1/categories', { headers: auth(ctx), data: { name, scope: 'DEPARTMENT', deptId } }),
    )
    expect(created.code, '指定部门的部门分类必须可建立（用户反馈 #1）').toBe(0)

    // 个人分类：前端据此显示「个人」标签（用户反馈 #1）
    const pName = uniq('API-个人分类')
    const personal = await body(await request.post('/api/v1/categories', { headers: auth(ctx), data: { name: pName, scope: 'PERSONAL' } }))
    expect(personal.code).toBe(0)

    const flat: any[] = []
    const walk = (ns: any[]) => (ns ?? []).forEach((n) => { flat.push(n); walk(n.children) })
    walk((await body(await request.get('/api/v1/categories', { headers: auth(ctx) }))).data)
    const node = flat.find((c) => c.name === name)
    expect(node, 'ADMIN 必须能看到部门分类（曾因只按 myDept 判定而不可见）').toBeTruthy()
    expect(node.scope).toBe('DEPARTMENT')
    expect(flat.find((c) => c.name === pName)?.scope, '分类须返回 scope，前端据此显示「个人」标签').toBe('PERSONAL')

    await request.delete(`/api/v1/categories/${created.data}`, { headers: auth(ctx) })
    await request.delete(`/api/v1/categories/${personal.data}`, { headers: auth(ctx) })
  })

  test('健康检查与 SPA 静态资源', async ({ request }) => {
    const health = await request.get('/actuator/health')
    expect(health.status()).toBe(200)
    expect((await health.json()).status).toBe('UP')

    const root = await request.get('/')
    expect(root.status()).toBe(200)
    expect(await root.text()).toContain('<div id="app"')

    const spa = await request.get('/some/deep/route', { headers: { Accept: 'text/html' } })
    expect(spa.status()).toBe(200)

    const missing = await body(await request.get('/api/v1/nope', { headers: auth(ctx) }))
    expect(missing.code).toBe(10004)
  })
})
