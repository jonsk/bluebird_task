import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useNoteStore } from '@/stores/note'

/**
 * noteStore：列表缓存键/淘汰/失效 + 会话快照（§8.4）。
 * 这些是 4 个列表页与右栏共用的状态层，缓存语义错会直接表现为「保存后列表没变」。
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
    expect(s.cacheKey('mine', {} as never)).not.toBe(s.cacheKey('subordinate', {} as never))
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
    s.cacheSet(s.cacheKey('subordinate', {} as never), page([2]))
    s.invalidate(['mine'])
    expect(s.cacheGet(s.cacheKey('mine', {} as never))).toBeUndefined()
    expect(s.cacheGet(s.cacheKey('subordinate', {} as never))?.data.total).toBe(1)
  })

  it('cacheClear 清空全部', () => {
    const s = useNoteStore()
    s.cacheSet(s.cacheKey('mine', {} as never), page([1]))
    s.cache.clear()
    expect(s.cache.size).toBe(0)
  })

  it('单 scope 条目数超上限时淘汰最旧的', () => {
    const s = useNoteStore()
    // CACHE_PER_SCOPE_MAX = 3：写 10 条后同 scope 最多留 3 条
    for (let i = 0; i < 10; i += 1) {
      s.cacheSet(s.cacheKey('mine', { keyword: `k${i}` } as never), page([i]))
    }
    const mineKeys = Array.from(s.cache.keys()).filter((k) => k.startsWith('mine|'))
    expect(mineKeys.length).toBeLessThanOrEqual(3)
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
    s.setQuery('subordinate', { keyword: 'internal-kw' } as never)
    expect(s.getQuery('mine').keyword).toBe('mine-kw')
    expect(s.getQuery('subordinate').keyword).toBe('internal-kw')
  })
})

describe('noteStore · 会话快照', () => {
  it('setActiveNote 经 watch 立即落 sessionStorage（未登录会话键为 guest）', async () => {
    const s = useNoteStore()
    s.setActiveNote(123, 'mine')
    await nextTick()
    const raw = JSON.parse(sessionStorage.getItem('note.session.guest') ?? '{}')
    expect(raw.activeNoteId).toBe(123)
    expect(raw.activeScopeId).toBe('mine')
  })

  it('restoreSession 恢复 activeNoteId / activeScopeId', () => {
    sessionStorage.setItem(
      'note.session.guest',
      JSON.stringify({ activeNoteId: 42, activeScopeId: 'all' }),
    )
    setActivePinia(createPinia())
    const fresh = useNoteStore()
    fresh.restoreSession()
    expect(fresh.activeNoteId).toBe(42)
    expect(fresh.activeScopeId).toBe('all')
  })

  it('reset 清空缓存与内存态，并删除该账号的会话键', () => {
    const s = useNoteStore()
    s.cacheSet(s.cacheKey('mine', {} as never), page([1]))
    s.setActiveNote(7, 'mine')
    s.reset(1)
    expect(s.cache.size).toBe(0)
    expect(s.activeNoteId).toBeNull()
    expect(s.activeScopeId).toBeNull()
    expect(sessionStorage.getItem('note.session.1')).toBeNull()
  })
})
