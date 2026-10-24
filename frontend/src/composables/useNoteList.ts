import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listNotes } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import type { NoteBrief, NoteQuery, NoteQueryPatch, NoteScope } from '@/types/note'

export interface UseNoteListOptions {
  isolated?: boolean
  isolatedKey?: string
  initialQuery?: NoteQueryPatch & { page?: number; size?: number }
}

export interface UseNoteListResult {
  notes: import('vue').Ref<NoteBrief[]>
  total: import('vue').Ref<number>
  page: import('vue').Ref<number>
  loading: import('vue').Ref<boolean>
  error: import('vue').Ref<Error | null>
  query: import('vue').ComputedRef<NoteQuery>
  setPage: (page: number) => void
  setQuery: (patch: NoteQueryPatch) => void
  resetPage: () => void
  reloadCurrent: () => void
  reloadFirst: () => void
  refresh: () => void
  needsReload: import('vue').ComputedRef<boolean>
}

function readQueryFromRoute(route: ReturnType<typeof useRoute>): NoteQueryPatch & { page?: number; size?: number } {
  const q = route.query
  const num = (v: unknown) => (v != null && v !== '' ? Number(v) : undefined)
  return {
    page: num(q.page) ?? 1,
    keyword: typeof q.keyword === 'string' ? q.keyword : undefined,
    tagId: num(q.tagId),
    tagName: typeof q.tagName === 'string' ? q.tagName : undefined,
    visibility: (q.visibility as NoteQuery['visibility']) || undefined,
    pinned: q.pinned === 'true' ? true : q.pinned === 'false' ? false : undefined,
    date: typeof q.date === 'string' ? q.date : undefined,
    start: typeof q.start === 'string' ? q.start : undefined,
    end: typeof q.end === 'string' ? q.end : undefined,
    sort: (q.sort as NoteQuery['sort']) || 'log_date_desc',
  }
}

export function useNoteList(options: UseNoteListOptions = {}): UseNoteListResult {
  const route = useRoute()
  const router = useRouter()
  const store = useNoteStore()
  const scope = computed<NoteScope>(() => (route.meta.scope as NoteScope) || 'mine')
  const archived = computed<boolean>(() => Boolean(route.meta.archived))

  const notes = ref<NoteBrief[]>([])
  const total = ref(0)
  const page = ref(options.initialQuery?.page ?? 1)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const needsReload = ref(false)
  let reqSeq = 0

  // 局部查询（非 isolated 时用 store.queryByScope，isolated 时仅局部）
  // 注意：isolated 实例需要自定义 size（右栏 5 条），故这里放宽到含 size
  const localQuery = ref<NoteQueryPatch & { size?: number }>({ ...(options.initialQuery || {}), sort: options.initialQuery?.sort || 'log_date_desc' })

  if (options.isolated) {
    Object.assign(localQuery.value, readQueryFromRoute(route), options.initialQuery || {})
  }

  const query = computed<NoteQuery>(() => {
    const base = options.isolated ? localQuery.value : store.getQuery(scope.value)
    // 注意顺序：base 里带 store 的旧 page/size，必须被当前实例的 page/size 覆盖，
    // 否则 setPage(3) 会被 store 里的 page=1 顶掉（分页永远停在第一页）。
    return {
      ...base,
      scope: scope.value,
      archived: archived.value,
      page: page.value,
      size: base.size ?? 20,
    }
  })

  function buildKey(): string {
    return store.cacheKey(scope.value, query.value)
  }

  async function run(): Promise<void> {
    const seq = ++reqSeq
    loading.value = true
    error.value = null
    const key = buildKey()
    // 缓存读取（仅非 isolated）
    if (!options.isolated) {
      const hit = store.cacheGet(key)
      if (hit) {
        notes.value = hit.data.list ?? []
        total.value = hit.data.total ?? 0
        const totalPages = Math.max(1, Math.ceil(total.value / (query.value.size ?? 20)))
        needsReload.value = page.value > totalPages
        loading.value = false
        return
      }
    }
    try {
      const data = await listNotes(query.value)
      if (seq !== reqSeq) return // 竞态：丢弃过期响应
      notes.value = data.list ?? []
      total.value = data.total ?? 0
      needsReload.value = false
      if (!options.isolated) store.cacheSet(key, data)
    } catch (e) {
      if (seq !== reqSeq) return
      error.value = e as Error
    } finally {
      if (seq === reqSeq) loading.value = false
    }
  }

  function syncUrl(): void {
    if (options.isolated) return
    const q: Record<string, string> = {}
    const p = query.value
    if (page.value !== 1) q.page = String(page.value)
    if (p.keyword) q.keyword = p.keyword
    if (p.tagId != null) q.tagId = String(p.tagId)
    if (p.tagName) q.tagName = p.tagName
    if (p.visibility) q.visibility = p.visibility
    if (p.pinned != null) q.pinned = String(p.pinned)
    if (p.date) q.date = p.date
    if (p.start) q.start = p.start
    if (p.end) q.end = p.end
    if (p.sort && p.sort !== 'log_date_desc') q.sort = p.sort
    router.replace({ path: route.path, query: q }).catch(() => {})
  }

  function setPage(p: number): void {
    page.value = p
    syncUrl()
    void run()
  }
  function setQuery(patch: NoteQueryPatch): void {
    if (options.isolated) {
      localQuery.value = { ...localQuery.value, ...patch }
    } else {
      store.setQuery(scope.value, { ...patch, page: 1 })
    }
    page.value = 1
    syncUrl()
    void run()
  }
  function resetPage(): void {
    page.value = 1
  }
  function reloadCurrent(): void {
    void run()
  }
  function reloadFirst(): void {
    page.value = 1
    void run()
  }
  function refresh(): void {
    void run()
  }

  const needsReloadComputed = computed(() => needsReload.value)

  // 缓存命中需要重载时自动重拉首页
  watch(needsReloadComputed, (v) => {
    if (v) reloadFirst()
  })

  // URL/路由变化（非 isolated 时监听 query）
  if (!options.isolated) {
    watch(
      () => route.query,
      () => {
        const fromRoute = readQueryFromRoute(route)
        store.setQuery(scope.value, { ...fromRoute, page: fromRoute.page ?? 1 })
        page.value = fromRoute.page ?? 1
        void run()
      },
    )
    watch(
      () => [route.meta.scope, route.meta.archived],
      () => {
        page.value = 1
        void run()
      },
    )
  }

  // 初始化
  if (!options.isolated) {
    const fromRoute = readQueryFromRoute(route)
    store.setQuery(scope.value, { ...fromRoute, page: fromRoute.page ?? 1 })
    page.value = fromRoute.page ?? 1
  }
  void run()

  // 组件卸载 / effectScope 停止时让在途请求失效（onScopeDispose 在两种场景都安全）
  onScopeDispose(() => {
    reqSeq++
  })

  return {
    notes,
    total,
    page,
    loading,
    error,
    query,
    setPage,
    setQuery,
    resetPage,
    reloadCurrent,
    reloadFirst,
    refresh,
    needsReload: needsReloadComputed,
  }
}
