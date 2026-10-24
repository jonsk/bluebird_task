import { ref, onScopeDispose } from 'vue'
import { listNoteTags } from '@/api/note'
import type { NoteTagFacetItem, NoteScope } from '@/types/note'

const CACHE_TTL_MS = 60_000
const cache = new Map<string, { data: NoteTagFacetItem[]; at: number }>()

/** 标签 facet 加载（按 scope，带 loading/error/retry 三态与 60s 缓存）。 */
export function useNoteFacets() {
  const tags = ref<NoteTagFacetItem[]>([])
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let seq = 0
  let lastScope: NoteScope | null = null
  let lastKeyword: string | undefined

  async function load(scope: NoteScope, keyword?: string): Promise<void> {
    const s = ++seq
    lastScope = scope
    lastKeyword = keyword
    const key = `${scope}|${keyword ?? ''}`
    const hit = cache.get(key)
    if (hit && Date.now() - hit.at < CACHE_TTL_MS) {
      tags.value = hit.data
      loading.value = false
      error.value = null
      return
    }
    loading.value = true
    error.value = null
    try {
      const data = await listNoteTags(scope, keyword)
      if (s !== seq) return
      tags.value = data
      cache.set(key, { data, at: Date.now() })
    } catch (e) {
      if (s !== seq) return
      error.value = e as Error
    } finally {
      if (s === seq) loading.value = false
    }
  }

  /** 重试：清缓存后按上次参数重新请求（不是只清 error 标记）。 */
  function retry(): void {
    if (lastScope == null) return
    cache.delete(`${lastScope}|${lastKeyword ?? ''}`)
    void load(lastScope, lastKeyword)
  }

  onScopeDispose(() => {
    seq++
  })

  return { tags, loading, error, load, retry }
}
