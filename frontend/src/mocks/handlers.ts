import { http, HttpResponse } from 'msw'

/**
 * MSW 契约 Mock（03 §2.6）。仅 dev/test 装载；端点按 openapi.yaml 生成。
 * M0：核心端点内联数据；后续接入 docs/frontend-baseline/fixtures（契约同源）。
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

export const handlers = [
  http.post(`${AUTH}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string }
    if (body.username === 'admin' && body.password === 'admin123') {
      return ok({ accessToken: 'mock-access', refreshToken: 'mock-refresh', mustChangePassword: false })
    }
    return fail(20003, '用户名或密码错误')
  }),

  http.post(`${AUTH}/auth/refresh`, () =>
    ok({ accessToken: 'mock-access', refreshToken: 'mock-refresh', mustChangePassword: false }),
  ),

  http.post(`${AUTH}/auth/logout`, () => ok(null)),

  http.get(`${AUTH}/auth/external/config`, () => ok({ provider: 'LOCAL' })),

  http.get(`${AUTH}/users/me`, () => ok(MOCK_USER)),
]
