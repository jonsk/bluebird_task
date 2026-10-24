import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useNoteStore } from '@/stores/note'
import { scopesForVisibility } from '@/composables/useNoteActions'

/**
 * noteStore：列表缓存键/淘汰/失效 + 会话快照（§8.4）。
 * 这些是 7 个列表页与右栏共用的状态层，缓存语义错会直接表现为「保存后列表没变」。
 */

const page = (ids: number[], total = ids.length) => ({
  list: ids.map((id) => ({ id })) as never,
  total,
  page: 1,
  size: 20,
})

beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
})

describe('noteStore · 缓存键', () => {
  it('键序不同的等价查询命中同一缓存条目', () => {
    const s = useNoteStore()
    const a = s.cacheKey('mine', { keyword: 'x', tagId: 1 } as never)
    const b = s.cacheKey('mine', { tagId: 1, keyword: 'x' } as never)
    expect(a).toBe(b)
  })

  it('scope 不同则键不同（避免跨作用域串数据）', () => {
    const s = useNoteStore()
    expect(s.cacheKey('mine', {} as never)).not.toBe(s.cacheKey('internal', {} as never))
  })

  it('scope 不参与键体重复：query 里的 scope 被剔除', () => {
    const s = useNoteStore()
    expect(s.cacheKey('mine', { scope: 'mine', keyword: 'k' } as never)).toBe(
      s.cacheKey('mine', { keyword: 'k' } as never),
    )
  })
})

describe('noteStore · 失效与读取', () => {
  it('invalidate 只清指定 scope', () => {
    const s = useNoteStore()
    s.cacheSet(s.cacheKey('mine', {} as never), page([1]))
    s.cacheSet(s.cacheKey('internal', {} as never), page([2]))
    s.invalidate(['mine'])
    expect(s.cacheGet(s.cacheKey('mine', {} as never))).toBeUndefined()
    expect(s.cacheGet(s.cacheKey('internal', {} as never))?.data.total).toBe(1)
  })

  it('cacheClear 清空全部', () => {
    const s = useNoteStore()
    s.cacheSet(s.cacheKey('mine', {} as never), page([1]))
    s.cache.clear()
    expect(s.cache.size).toBe(0)
  })

  it('单 scope 条目数超上限时淘汰最旧的', () => {
    const s = useNoteStore()
    // CACHE_PER_SCOPE_MAX = 8：写 10 条后同 scope 最多留 8 条
    for (let i = 0; i < 10; i += 1) {
      s.cacheSet(s.cacheKey('mine', { keyword: `k${i}` } as never), page([i]))
    }
    const mineKeys = Array.from(s.cache.keys()).filter((k) => k.startsWith('mine|'))
    expect(mineKeys.length).toBeLessThanOrEqual(8)
  })
})

describe('noteStore · 查询状态', () => {
  it('setQuery 只合并 patch（page 由 useNoteList 负责重置），setPage 保留筛选', () => {
    const s = useNoteStore()
    s.setQuery('mine', { keyword: 'a' } as never)
    expect(s.getQuery('mine').keyword).toBe('a')
    expect(s.getQuery('mine').page).toBe(1)

    s.setPage('mine', 2)
    expect(s.getQuery('mine').page).toBe(2)
    expect(s.getQuery('mine').keyword).toBe('a')
  })

  it('作用域之间筛选互不影响', () => {
    const s = useNoteStore()
    s.setQuery('mine', { keyword: 'mine-kw' } as never)
    s.setQuery('internal', { keyword: 'internal-kw' } as never)
    expect(s.getQuery('mine').keyword).toBe('mine-kw')
    expect(s.getQuery('internal').keyword).toBe('internal-kw')
  })
})

describe('noteStore · 会话快照（临时卡片跨路由恢复）', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-28T10:00:00+08:00'))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('setPending 经 watch 立即落 sessionStorage（未登录会话键为 guest）', async () => {
    const s = useNoteStore()
    s.setPending(123)
    await nextTick()
    const raw = JSON.parse(sessionStorage.getItem('note.session.guest') ?? '{}')
    expect(raw.pendingCreatedNoteId?.id).toBe(123)
  })

  it('restoreSession：TTL 内恢复，超过 30 分钟丢弃', () => {
    const payload = (ts: number) =>
      JSON.stringify({ activeNoteId: null, activeScopeId: null, pendingCreatedNoteId: { id: 123, ts } })

    sessionStorage.setItem('note.session.guest', payload(Date.now()))
    setActivePinia(createPinia())
    const fresh = useNoteStore()
    fresh.restoreSession()
    expect(fresh.pendingCreatedNoteId?.id).toBe(123)

    sessionStorage.setItem('note.session.guest', payload(Date.now() - 31 * 60 * 1000))
    setActivePinia(createPinia())
    const expired = useNoteStore()
    expired.restoreSession()
    expect(expired.pendingCreatedNoteId).toBeNull()
  })

  it('reset 清空缓存与内存态，并删除该账号的会话键', () => {
    const s = useNoteStore()
    s.cacheSet(s.cacheKey('mine', {} as never), page([1]))
    s.setPending(789)
    sessionStorage.setItem('note.session.1', '{"pendingCreatedNoteId":null}')
    s.reset(1)
    expect(s.cache.size).toBe(0)
    expect(s.pendingCreatedNoteId).toBeNull()
    expect(sessionStorage.getItem('note.session.1')).toBeNull()
  })
})

describe('useNoteActions · 可见性 → 受影响 scope', () => {
  it('PRIVATE 只影响自己与管理员视图', () => {
    expect(scopesForVisibility('PRIVATE')).toEqual(['mine', 'all'])
  })
  it('INTERNAL 额外影响本部门', () => {
    expect(scopesForVisibility('INTERNAL')).toContain('internal')
  })
  it('PUBLIC 影响探索与共享', () => {
    expect(scopesForVisibility('PUBLIC')).toEqual(expect.arrayContaining(['explore', 'shared']))
  })
})
