import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'

/**
 * useNoteFacets：按 scope 取标签、60s 缓存命中、retry 清缓存、错误态、竞态。
 * 缓存是模块级，用例用不同 scope/keyword 隔离。
 */

const listNoteTags = vi.fn()
vi.mock('@/api/note', () => ({
  listNoteTags: (...args: unknown[]) => listNoteTags(...args),
}))

import { useNoteFacets } from '@/composables/useNoteFacets'

let scopes: EffectScope[] = []

function mount() {
  const scope = effectScope()
  const r = scope.run(() => useNoteFacets())!
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
  listNoteTags.mockReset()
  scopes = []
})

describe('useNoteFacets', () => {
  it('按 scope 取数并落 tags', async () => {
    const r = mount()
    listNoteTags.mockResolvedValue([{ id: 1, name: '周报', count: 3 }])
    await r.load('mine')
    expect(listNoteTags).toHaveBeenCalledWith('mine', undefined)
    expect(r.tags.value).toHaveLength(1)
    expect(r.loading.value).toBe(false)
    expect(r.error.value).toBeNull()
  })

  it('60s 内同 scope+keyword 命中缓存，不再请求', async () => {
    listNoteTags.mockResolvedValue([{ id: 2, name: '缓存', count: 1 }])
    const a = mount()
    await a.load('explore', 'kw')
    const b = mount()
    listNoteTags.mockClear()
    await b.load('explore', 'kw')
    expect(listNoteTags).not.toHaveBeenCalled()
    expect(b.tags.value[0]).toMatchObject({ name: '缓存' })
  })

  it('retry 清缓存后重新请求（不是只清 error）', async () => {
    listNoteTags.mockRejectedValueOnce(new Error('网络错误'))
    const r = mount()
    await r.load('internal', 'retry-kw')
    expect(r.error.value).toBeInstanceOf(Error)

    listNoteTags.mockResolvedValueOnce([{ id: 3, name: '重试成功', count: 1 }])
    r.retry()
    await flush()
    expect(listNoteTags).toHaveBeenCalledTimes(2)
    expect(r.error.value).toBeNull()
    expect(r.tags.value[0]).toMatchObject({ name: '重试成功' })
  })

  it('未加载过时 retry 不发请求', () => {
    const r = mount()
    r.retry()
    expect(listNoteTags).not.toHaveBeenCalled()
  })

  it('失败后不写缓存：再次 load 会重新请求', async () => {
    listNoteTags.mockRejectedValueOnce(new Error('boom'))
    const r = mount()
    await r.load('subordinate', 'nocache')
    listNoteTags.mockResolvedValueOnce([{ id: 4, name: 'x', count: 0 }])
    await r.load('subordinate', 'nocache')
    expect(listNoteTags).toHaveBeenCalledTimes(2)
  })

  it('竞态：旧请求晚到不覆盖新结果', async () => {
    const r = mount()
    let slow: (v: unknown) => void = () => {}
    listNoteTags.mockImplementationOnce(() => new Promise((res) => (slow = res)))
    const p1 = r.load('visible', 'k1')
    listNoteTags.mockResolvedValueOnce([{ id: 5, name: 'new', count: 1 }])
    await r.load('visible', 'k2')
    slow([{ id: 6, name: 'old', count: 9 }])
    await p1
    await flush()
    expect(r.tags.value[0]).toMatchObject({ name: 'new' })
  })
})
