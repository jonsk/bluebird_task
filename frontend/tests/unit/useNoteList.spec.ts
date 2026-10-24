import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope, nextTick, reactive, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

/* ---------- mocks：路由 + 接口 ---------- */
const route = reactive({
  path: '/notes',
  query: {} as Record<string, string>,
  meta: { scope: 'mine', archived: false } as Record<string, unknown>,
})
type RouteLocation = { path: string; query: Record<string, string> }
const router = { replace: vi.fn((_loc: RouteLocation) => Promise.resolve()) }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

const listNotes = vi.fn()
vi.mock('@/api/note', () => ({
  listNotes: (...args: unknown[]) => listNotes(...args),
}))

import { useNoteList, type UseNoteListOptions, type UseNoteListResult } from '@/composables/useNoteList'
import { useNoteStore } from '@/stores/note'

const pageData = (ids: number[], total = ids.length, p = 1, size = 20) => ({
  list: ids.map((id) => ({ id })),
  total,
  page: p,
  size,
})

async function flush(times = 4): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

// 每个实例放进独立 effectScope，测试结束统一 stop，避免 watcher 跨用例互相触发
let scopes: EffectScope[] = []
function mountList(options?: UseNoteListOptions): UseNoteListResult {
  const scope = effectScope()
  const result = scope.run(() => useNoteList(options))!
  scopes.push(scope)
  return result
}

beforeEach(() => {
  setActivePinia(createPinia())
  route.path = '/notes'
  route.query = {}
  route.meta = { scope: 'mine', archived: false }
  listNotes.mockReset()
  router.replace.mockClear()
  scopes = []
})

afterEach(() => {
  scopes.forEach((s) => s.stop())
})

describe('useNoteList · 缓存归属', () => {
  it('非 isolated：首次请求写 store 缓存，同 key 的第二个实例直接命中不再请求', async () => {
    listNotes.mockResolvedValue(pageData([1, 2], 2))
    const store = useNoteStore()

    const a = mountList()
    await flush()
    expect(listNotes).toHaveBeenCalledTimes(1)
    expect(store.cache.size).toBe(1)
    expect(a.notes.value.map((n) => n.id)).toEqual([1, 2])

    const b = mountList()
    await flush()
    expect(listNotes).toHaveBeenCalledTimes(1) // 命中缓存，无第二次请求
    expect(b.notes.value.map((n) => n.id)).toEqual([1, 2])
    expect(b.loading.value).toBe(false)
  })

  it('isolated：不读写 store.cache，也不改 store.queryByScope', async () => {
    listNotes.mockResolvedValue(pageData([7], 1, 1, 5))
    const store = useNoteStore()
    const before = JSON.stringify(store.queryByScope.mine)

    const list = mountList({ isolated: true, initialQuery: { date: '2026-10-23', page: 1, size: 5 } })
    await flush()

    expect(listNotes).toHaveBeenCalledTimes(1)
    expect(store.cache.size).toBe(0)
    expect(JSON.stringify(store.queryByScope.mine)).toBe(before)
    // 右栏 5 条：自定义 size 必须生效（不能回落到 20）
    expect(listNotes).toHaveBeenCalledWith(expect.objectContaining({ size: 5, date: '2026-10-23' }))
    expect(list.query.value.size).toBe(5)
  })
})

describe('useNoteList · 分页与 URL', () => {
  it('setPage(3) 请求 page=3 并写回 URL', async () => {
    listNotes.mockResolvedValue(pageData([1], 50))
    const list = mountList()
    await flush()
    listNotes.mockClear()

    list.setPage(3)
    await flush()

    expect(listNotes).toHaveBeenCalledWith(expect.objectContaining({ page: 3 }))
    expect(router.replace).toHaveBeenCalled()
    const last = router.replace.mock.calls[router.replace.mock.calls.length - 1][0]
    expect(last.query.page).toBe('3')
  })

  it('setQuery 重置 page=1，且 scope/隐藏字段不进 URL', async () => {
    listNotes.mockResolvedValue(pageData([1], 50))
    route.query = { page: '3' }
    const list = mountList()
    await flush()
    listNotes.mockClear()

    list.setQuery({ keyword: '周报' })
    await flush()

    expect(listNotes).toHaveBeenCalledWith(expect.objectContaining({ page: 1, keyword: '周报' }))
    const last = router.replace.mock.calls[router.replace.mock.calls.length - 1][0]
    expect(last.query.page).toBeUndefined()
    expect(last.query.keyword).toBe('周报')
    expect(last.query.scope).toBeUndefined()
  })
})

describe('useNoteList · needsReload', () => {
  it('缓存命中且 page 超出总页数 → 自动 reloadFirst（不会永远停在空页）', async () => {
    const store = useNoteStore()
    route.query = { page: '3' }
    // 预置一条"第 3 页，但总数只有 1 页"的缓存（key 与 composable 内部一致）
    const key = store.cacheKey('mine', {
      archived: false,
      page: 3,
      size: 20,
      sort: 'log_date_desc',
    } as never)
    store.cacheSet(key, pageData([], 5, 3))

    listNotes.mockResolvedValue(pageData([42], 5, 1))
    const list = mountList()
    await flush(8)

    expect(listNotes).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
    expect(list.page.value).toBe(1)
    expect(list.notes.value.map((n) => n.id)).toEqual([42])
    expect(list.needsReload.value).toBe(false)
  })
})

describe('useNoteList · 竞态', () => {
  it('旧请求的响应迟到时被丢弃，不覆盖新结果', async () => {
    let resolveFirst: (v: unknown) => void = () => {}
    const first = new Promise((r) => {
      resolveFirst = r
    })
    listNotes.mockReturnValueOnce(first)
    listNotes.mockResolvedValueOnce(pageData([2], 2))

    const list = mountList()
    await flush()
    expect(listNotes).toHaveBeenCalledTimes(1)

    list.setQuery({ keyword: 'x' })
    await flush()
    expect(list.notes.value.map((n) => n.id)).toEqual([2])

    resolveFirst(pageData([1], 1)) // 第一次迟到的响应
    await flush()

    expect(list.notes.value.map((n) => n.id)).toEqual([2])
  })
})

describe('note 缓存策略', () => {
  it('TTL 过期视为未命中并删除条目', () => {
    const store = useNoteStore()
    store.cacheSet('mine|k', pageData([1], 1))
    store.cache.get('mine|k')!.updatedAt = Date.now() - 61_000

    expect(store.cacheGet('mine|k')).toBeUndefined()
    expect(store.cache.has('mine|k')).toBe(false)
  })

  it('单 scope 最多 3 条，全局最多 20 条', () => {
    const store = useNoteStore()
    const scopes6 = ['mine', 'shared', 'explore', 'internal', 'subordinate', 'all'] as const

    for (const s of scopes6) {
      for (let i = 0; i < 4; i += 1) store.cacheSet(`${s}|p${i}`, pageData([i], 1))
    }

    expect(store.cache.size).toBeLessThanOrEqual(20)
    for (const s of scopes6) {
      expect([...store.cache.keys()].filter((k) => k.startsWith(`${s}|`)).length).toBeLessThanOrEqual(3)
    }
    // 保留较新的：p0 应被淘汰、p3 应保留
    expect(store.cache.has('mine|p0')).toBe(false)
    expect(store.cache.has('mine|p3')).toBe(true)
  })

  it('invalidate 按 scope 前缀清理，不误伤其他 scope', () => {
    const store = useNoteStore()
    store.cacheSet('mine|a', pageData([1], 1))
    store.cacheSet('explore|a', pageData([2], 1))

    store.invalidate(['mine'])

    expect(store.cache.has('mine|a')).toBe(false)
    expect(store.cache.has('explore|a')).toBe(true)
  })
})
