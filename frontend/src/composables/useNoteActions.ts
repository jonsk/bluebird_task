import { ElMessage } from 'element-plus'
import { pinNote, unpinNote, archiveNote, unarchiveNote, deleteNote } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import { HttpError, BizError } from '@/api/http'
import type { NoteVisibility, NoteScope } from '@/types/note'

/** 变更前 visibility → 受影响 scope 集合（用于缓存失效）。 */
export function scopesForVisibility(v?: NoteVisibility): NoteScope[] {
  switch (v) {
    case 'PRIVATE':
      return ['mine', 'all']
    case 'INTERNAL':
      return ['mine', 'internal', 'all']
    case 'PUBLIC':
      return ['mine', 'explore', 'shared', 'all']
    default:
      return ['mine', 'all']
  }
}

function hint(e: unknown): string {
  if (e instanceof BizError) return e.message
  if (e instanceof HttpError) return e.message
  return (e as Error).message || '操作失败'
}

/** 笔记动作单点（7 页共用）：pin/archive/remove + 缓存失效 + toast。 */
export function useNoteActions() {
  const store = useNoteStore()

  function onChanged(visibility?: NoteVisibility): void {
    store.invalidate(scopesForVisibility(visibility))
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

  async function toggleArchive(note: { id: number; archived?: boolean; visibility?: NoteVisibility }): Promise<void> {
    try {
      if (note.archived) await unarchiveNote(note.id)
      else await archiveNote(note.id)
      ElMessage.success('操作成功')
      // 归档额外清 mine/all 的 archived 查询
      store.invalidate([...scopesForVisibility(note.visibility), 'mine', 'all'])
    } catch (e) {
      ElMessage.error(hint(e))
    }
  }

  async function remove(note: { id: number; visibility?: NoteVisibility }): Promise<void> {
    try {
      await deleteNote(note.id)
      ElMessage.success('已删除')
      onChanged(note.visibility)
    } catch (e) {
      ElMessage.error(hint(e))
    }
  }

  return { onChanged, togglePin, toggleArchive, remove }
}
