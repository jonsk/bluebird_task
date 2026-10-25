import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'

/**
 * useNoteDetail：竞态取最后一次、404/50001 → notFound、reload 复用 id。
 * 初始 URL/store：不依赖路由与 store。
 */

const getNote = vi.fn()
vi.mock('@/api/note', () => ({
  getNote: (...args: unknown[]) => getNote(...args),
}))

import { useNoteDetail } from '@/composables/useNoteDetail'
import { HttpError, BizError } from '@/api/http'

let scopes: EffectScope[] = []

function mount() {
  const scope = effectScope()
  const r = scope.run(() => useNoteDetail())!
  scopes.push(scope)
  return r
}

async function flush(times = 4): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

beforeEach(() => {
  getNote.mockReset()
  scopes = []
})

describe('useNoteDetail', () => {
  it('加载成功：detail 落地、loading 收尾、无错误', async () => {
    const r = mount()
    getNote.mockResolvedValue({ id: 1001, content: '正文', version: 0 })
    void r.load(1001)
    expect(r.loading.value).toBe(true)
    await flush()
    expect(r.detail.value).toMatchObject({ id: 1001, content: '正文' })
    expect(r.loading.value).toBe(false)
    expect(r.error.value).toBeNull()
    expect(r.notFound.value).toBe(false)
  })

  it('404 → notFound=true 且 error 保留', async () => {
    const r = mount()
    getNote.mockRejectedValue(new HttpError(404, 404, '资源不存在'))
    await r.load(999999)
    expect(r.notFound.value).toBe(true)
    expect(r.error.value).toBeInstanceOf(HttpError)
    expect(r.loading.value).toBe(false)
  })

  it('50001 业务码同样判定为 notFound', async () => {
    const r = mount()
    getNote.mockRejectedValue(new BizError(50001, '日志不存在'))
    await r.load(1)
    expect(r.notFound.value).toBe(true)
  })

  it('403 不判定 notFound（是权限问题，需重试入口）', async () => {
    const r = mount()
    getNote.mockRejectedValue(new HttpError(403, undefined, '无权限'))
    await r.load(1)
    expect(r.notFound.value).toBe(false)
    expect(r.error.value).toBeInstanceOf(HttpError)
  })

  it('竞态：慢的先返回不覆盖后发结果', async () => {
    const r = mount()
    let resolveSlow: (v: unknown) => void = () => {}
    getNote.mockImplementationOnce(() => new Promise((res) => (resolveSlow = res)))
    const first = r.load(1)
    getNote.mockResolvedValueOnce({ id: 2, content: '第二次' })
    await r.load(2)
    resolveSlow({ id: 1, content: '第一次' })
    await first
    await flush()
    expect(r.detail.value).toMatchObject({ id: 2, content: '第二次' })
    expect(r.loading.value).toBe(false)
  })

  it('reload 用当前 detail 的 id 重新请求；无 detail 时不动', async () => {
    const r = mount()
    r.reload()
    expect(getNote).not.toHaveBeenCalled()

    getNote.mockResolvedValue({ id: 7 })
    await r.load(7)
    getNote.mockResolvedValue({ id: 7, content: '刷新后' })
    r.reload()
    await flush()
    expect(getNote).toHaveBeenLastCalledWith(7)
    expect(r.detail.value).toMatchObject({ content: '刷新后' })
  })
})
