import { ElMessage } from 'element-plus'
import { pinNote, unpinNote, deleteNote } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import { HttpError, BizError } from '@/api/http'
import type { NoteScope } from '@/types/note'

/** 变更后受影响的 scope 集合（用于缓存失效；日志只有「我的 / 被授权 / 员工 / 全部」四处）。 */
export const AFFECTED_SCOPES: NoteScope[] = ['mine', 'subordinate', 'all']

function hint(e: unknown): string {
  if (e instanceof BizError) return e.message
  if (e instanceof HttpError) return e.message
  return (e as Error).message || '操作失败'
}

/** 日志动作单点（4 页共用）：pin/remove + 缓存失效 + toast。 */
export function useNoteActions() {
  const store = useNoteStore()

  function onChanged(): void {
    store.invalidate(AFFECTED_SCOPES)
  }

  async function togglePin(note: { id: number; pinned?: boolean }): Promise<void> {
    try {
      if (note.pinned) await unpinNote(note.id)
      else await pinNote(note.id)
      ElMessage.success('操作成功')
      onChanged()
    } catch (e) {
      ElMessage.error(hint(e))
    }
  }

  async function remove(note: { id: number }): Promise<void> {
    try {
      await deleteNote(note.id)
      ElMessage.success('已删除')
      onChanged()
    } catch (e) {
      ElMessage.error(hint(e))
    }
  }

  return { onChanged, togglePin, remove }
}
