import { ref, onUnmounted } from 'vue'
import { getNote } from '@/api/note'
import { HttpError, BizError } from '@/api/http'
import type { NoteDetailVO } from '@/types/note'

/** 详情加载（竞态取消 + reload；404/50001 → notFound）。 */
export function useNoteDetail() {
  const detail = ref<NoteDetailVO | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const notFound = ref(false)
  let seq = 0

  async function load(id: number): Promise<void> {
    const s = ++seq
    loading.value = true
    error.value = null
    notFound.value = false
    try {
      const d = await getNote(id)
      if (s !== seq) return
      detail.value = d
    } catch (e) {
      if (s !== seq) return
      if ((e instanceof HttpError && e.status === 404) || (e instanceof BizError && e.code === 50001)) {
        notFound.value = true
      }
      error.value = e as Error
    } finally {
      if (s === seq) loading.value = false
    }
  }

  function reload(): void {
    if (detail.value?.id) load(detail.value.id)
  }

  onUnmounted(() => {
    seq++
  })

  return { detail, loading, error, notFound, load, reload }
}
