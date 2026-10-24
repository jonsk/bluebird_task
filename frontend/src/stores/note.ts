import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { useAuthStore } from './auth'
import type { NoteQuery, NoteScope, NoteListData } from '@/types/note'

export const CACHE_TTL_MS = 60_000
const CACHE_MAX_KEYS = 20
const CACHE_PER_SCOPE_MAX = 3
const PENDING_TTL_MS = 30 * 60 * 1000

export interface NoteSessionState {
  activeNoteId: number | null
  activeScopeId: NoteScope | null
  pendingCreatedNoteId: { id: number; ts: number } | null
}

interface CacheEntry {
  data: NoteListData
  updatedAt: number
}

function defaultQuery(): NoteQuery {
  return { page: 1, size: 20, sort: 'log_date_desc' }
}

export const useNoteStore = defineStore('note', () => {
  const scopes: NoteScope[] = ['mine', 'shared', 'explore', 'internal', 'subordinate', 'visible', 'all']
  const queryByScope = reactive<Record<NoteScope, NoteQuery>>(
    Object.fromEntries(scopes.map((s) => [s, defaultQuery()])) as Record<NoteScope, NoteQuery>,
  )

  const cache = reactive(new Map<string, CacheEntry>())
  const activeNoteId = ref<number | null>(null)
  const activeScopeId = ref<NoteScope | null>(null)
  const pendingCreatedNoteId = ref<{ id: number; ts: number } | null>(null)

  // ---- 查询状态（仅由 useNoteList 经此写入）----
  function getQuery(scope: NoteScope): NoteQuery {
    return queryByScope[scope]
  }
  function setQuery(scope: NoteScope, patch: Partial<NoteQuery>): void {
    Object.assign(queryByScope[scope], patch)
  }
  function setPage(scope: NoteScope, page: number): void {
    queryByScope[scope].page = page
  }

  // ---- active / pending ----
  function setActiveNote(id: number | null, scope?: NoteScope): void {
    activeNoteId.value = id
    activeScopeId.value = id == null ? null : scope ?? activeScopeId.value
  }
  function clearActive(): void {
    activeNoteId.value = null
    activeScopeId.value = null
  }
  function setPending(id: number): void {
    pendingCreatedNoteId.value = { id, ts: Date.now() }
  }
  function clearPending(): void {
    pendingCreatedNoteId.value = null
  }

  // ---- 缓存 ----
  /** 稳定序列化：与对象键的插入顺序无关，避免"同一查询算出两个 key"。 */
  function stableStringify(v: unknown): string {
    if (v === null || typeof v !== 'object') return JSON.stringify(v) ?? 'null'
    if (Array.isArray(v)) return `[${v.map(stableStringify).join(',')}]`
    const o = v as Record<string, unknown>
    const keys = Object.keys(o)
      .filter((k) => o[k] !== undefined)
      .sort()
    return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(o[k])}`).join(',')}}`
  }
  function cacheKey(scope: NoteScope, query: NoteQuery): string {
    const { scope: _s, ...rest } = query as NoteQuery & { scope?: NoteScope }
    return `${scope}|${stableStringify(rest)}`
  }
  function cacheGet(key: string): CacheEntry | undefined {
    const e = cache.get(key)
    if (!e) return undefined
    if (Date.now() - e.updatedAt > CACHE_TTL_MS) {
      cache.delete(key)
      return undefined
    }
    return e
  }
  function cacheSet(key: string, data: NoteListData): void {
    cache.set(key, { data, updatedAt: Date.now() })
    evictIfNeeded()
  }
  function cacheDelete(key: string): void {
    cache.delete(key)
  }
  function cacheClear(): void {
    cache.clear()
  }
  function evictIfNeeded(): void {
    // 1) 每个 scope 严格至多 CACHE_PER_SCOPE_MAX 条（不能等全局超限才裁）
    const byScope = new Map<string, string[]>()
    for (const k of cache.keys()) {
      const s = k.split('|')[0]
      if (!byScope.has(s)) byScope.set(s, [])
      byScope.get(s)!.push(k)
    }
    for (const [, keys] of byScope) {
      if (keys.length > CACHE_PER_SCOPE_MAX) {
        const sorted = keys
          .sort((a, b) => (cache.get(a)?.updatedAt ?? 0) - (cache.get(b)?.updatedAt ?? 0))
        while (sorted.length > CACHE_PER_SCOPE_MAX) {
          const old = sorted.shift()!
          cache.delete(old)
        }
      }
    }
    // 2) 全局兜底：超过 CACHE_MAX_KEYS 时继续淘汰最旧
    while (cache.size > CACHE_MAX_KEYS) {
      let oldest: string | null = null
      let oldestAt = Infinity
      for (const [k, v] of cache) {
        if (v.updatedAt < oldestAt) {
          oldestAt = v.updatedAt
          oldest = k
        }
      }
      if (!oldest) break
      cache.delete(oldest)
    }
  }

  function invalidate(scopeList: NoteScope[]): void {
    for (const s of scopeList) {
      for (const k of Array.from(cache.keys())) {
        if (k.startsWith(`${s}|`)) cache.delete(k)
      }
    }
  }

  // ---- session ----
  function sessionKey(): string {
    const auth = useAuthStore()
    const uid = auth.user?.id ?? 'guest'
    return `note.session.${uid}`
  }
  function persistSession(): void {
    const payload: NoteSessionState = {
      activeNoteId: activeNoteId.value,
      activeScopeId: activeScopeId.value,
      pendingCreatedNoteId: pendingCreatedNoteId.value,
    }
    sessionStorage.setItem(sessionKey(), JSON.stringify(payload))
  }
  function restoreSession(): void {
    const raw = sessionStorage.getItem(sessionKey())
    if (!raw) return
    try {
      const p = JSON.parse(raw) as NoteSessionState
      activeNoteId.value = p.activeNoteId
      activeScopeId.value = p.activeScopeId
      pendingCreatedNoteId.value =
        p.pendingCreatedNoteId && Date.now() - p.pendingCreatedNoteId.ts <= PENDING_TTL_MS
          ? p.pendingCreatedNoteId
          : null
    } catch {
      /* ignore */
    }
  }
  function reset(userId: number): void {
    cacheClear()
    activeNoteId.value = null
    activeScopeId.value = null
    pendingCreatedNoteId.value = null
    sessionStorage.removeItem(`note.session.${userId}`)
    for (const s of scopes) Object.assign(queryByScope[s], defaultQuery())
  }

  return {
    queryByScope,
    cache,
    activeNoteId,
    activeScopeId,
    pendingCreatedNoteId,
    getQuery,
    setQuery,
    setPage,
    setActiveNote,
    clearActive,
    setPending,
    clearPending,
    cacheKey,
    cacheGet,
    cacheSet,
    cacheDelete,
    cacheClear,
    invalidate,
    persistSession,
    restoreSession,
    reset,
  }
})
