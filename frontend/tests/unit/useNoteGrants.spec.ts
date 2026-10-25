import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'

/** useNoteGrants：分页取数（本地分页包装）、setPage、错误态、竞态。 */

const listGrantsPaged = vi.fn()
vi.mock('@/api/note', () => ({
  listGrantsPaged: (...args: unknown[]) => listGrantsPaged(...args),
}))

import { useNoteGrants } from '@/composables/useNoteGrants'

let scopes: EffectScope[] = []

function mount() {
  const scope = effectScope()
  const r = scope.run(() => useNoteGrants())!
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
  listGrantsPaged.mockReset()
  scopes = []
})

describe('useNoteGrants', () => {
  it('默认 page=1 size=10，load 透传 tab/filters', async () => {
    const r = mount()
    listGrantsPaged.mockResolvedValue({ list: [{ id: 7001 }], total: 1 })
    await r.load('created', { status: 'PENDING' })
    expect(listGrantsPaged).toHaveBeenCalledWith('created', 1, 10, { status: 'PENDING' })
    expect(r.list.value).toHaveLength(1)
    expect(r.total.value).toBe(1)
    expect(r.loading.value).toBe(false)
  })

  it('setPage 后 load 使用新页码', async () => {
    const r = mount()
    listGrantsPaged.mockResolvedValue({ list: [], total: 0 })
    r.setPage(3)
    await r.load('granted')
    expect(listGrantsPaged).toHaveBeenCalledWith('granted', 3, 10, undefined)
  })

  it('失败置 error 并结束 loading，不清空已有列表', async () => {
    const r = mount()
    listGrantsPaged.mockResolvedValueOnce({ list: [{ id: 1 }], total: 1 })
    await r.load('incoming')
    listGrantsPaged.mockRejectedValueOnce(new Error('网络错误'))
    await r.load('incoming')
    expect(r.error.value).toBeInstanceOf(Error)
    expect(r.loading.value).toBe(false)
    expect(r.list.value).toHaveLength(1)
  })

  it('竞态：先发后到的结果被丢弃', async () => {
    const r = mount()
    let slow: (v: unknown) => void = () => {}
    listGrantsPaged.mockImplementationOnce(() => new Promise((res) => (slow = res)))
    const p1 = r.load('all')
    listGrantsPaged.mockResolvedValueOnce({ list: [{ id: 2 }], total: 1 })
    await r.load('all')
    slow({ list: [{ id: 1 }], total: 9 })
    await p1
    await flush()
    expect(r.list.value[0]).toMatchObject({ id: 2 })
    expect(r.total.value).toBe(1)
  })

  it('重新 load 前清掉上一次 error', async () => {
    const r = mount()
    listGrantsPaged.mockRejectedValueOnce(new Error('x'))
    await r.load('created')
    expect(r.error.value).toBeInstanceOf(Error)
    listGrantsPaged.mockResolvedValueOnce({ list: [], total: 0 })
    await r.load('created')
    expect(r.error.value).toBeNull()
  })
})
