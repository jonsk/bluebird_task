import { ref, onUnmounted } from 'vue'
import { listGrantsPaged } from '@/api/note'
import type { NoteUserGrantVO, NoteGrantListData } from '@/types/note'

/** 授权页数据（按 tab 分页/筛选/错误）。 */
export function useNoteGrants() {
  const list = ref<NoteUserGrantVO[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let seq = 0

  async function load(
    tab: string,
    filters?: { status?: string; role?: string; mode?: string },
  ): Promise<void> {
    const s = ++seq
    loading.value = true
    error.value = null
    try {
      const d: NoteGrantListData = await listGrantsPaged(tab, page.value, size.value, filters)
      if (s !== seq) return
      list.value = d.list
      total.value = d.total
    } catch (e) {
      if (s !== seq) return
      error.value = e as Error
    } finally {
      if (s === seq) loading.value = false
    }
  }

  function setPage(p: number): void {
    page.value = p
  }

  onUnmounted(() => {
    seq++
  })

  return { list, total, page, size, loading, error, load, setPage }
}
