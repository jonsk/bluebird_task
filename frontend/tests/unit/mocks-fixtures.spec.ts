import { describe, expect, it } from 'vitest'
import { dataOf, envelope, fixtureKeys } from '@/mocks/fixtures'

/**
 * MSW 直连基线 fixtures 的覆盖度/结构回归（`docs/frontend-baseline/fixtures/api/**`）。
 * 若 glob 路径或 fixture 目录结构漂移，此处立即失败（避免 mock 静默失效）。
 */
describe('MSW 契约 fixtures 直连', () => {
  it('注册全部基线 fixtures 且关键键存在', () => {
    const keys = fixtureKeys()
    expect(keys.length).toBeGreaterThan(40)
    for (const k of [
      'tasks/GET.list',
      'tasks/GET.detail',
      'tasks/GET.count',
      'tasks/GET.calendar',
      'tasks/GET.subtasks',
      'users/GET.me',
      'users/GET.index',
      'departments/GET.tree',
      'categories/GET.tree',
      'menus/GET.list',
      'tags/GET.list',
      'audit/GET.operates',
      'audit/GET.logins',
      'auth/POST.login',
      '_errors/10001.param',
    ]) {
      expect(keys, `缺失 fixture: ${k}`).toContain(k)
    }
  })

  it('任务列表为成功信封，含 7 条（E-16 周期样例 ≥3）', () => {
    const page = dataOf<{ list: { id: number; cycleRule?: unknown }[]; total: number }>('tasks/GET.list')
    expect(page.total).toBe(7)
    expect(page.list).toHaveLength(7)
    expect(page.list.filter((t) => t.cycleRule).length).toBeGreaterThanOrEqual(3)
  })

  it('身份对齐：当前用户 id=1（0304/G1）', () => {
    expect(dataOf<{ id: number; username: string }>('users/GET.me').id).toBe(1)
  })

  it('错误态信封 code != 0', () => {
    expect(envelope('_errors/10006.version-conflict').code).toBe(10006)
    expect(envelope('_errors/20003.bad-credentials').code).toBe(20003)
  })

  it('未注册 key 抛错', () => {
    expect(() => envelope('nope/GET.none')).toThrow(/fixture not found/)
  })
})
